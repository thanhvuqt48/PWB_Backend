package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.TrackStatus;
import com.fpt.producerworkbench.configuration.GcpProperties;
import com.fpt.producerworkbench.dto.event.LyricsExtractedEvent;
import com.fpt.producerworkbench.entity.InspirationTrack;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.TrackRepository;
import com.fpt.producerworkbench.service.TranscribeGcpService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.auth.oauth2.GoogleCredentials;

import com.google.cloud.speech.v2.AutoDetectDecodingConfig;
import com.google.cloud.speech.v2.BatchRecognizeFileMetadata;
import com.google.cloud.speech.v2.BatchRecognizeFileResult;
import com.google.cloud.speech.v2.BatchRecognizeRequest;
import com.google.cloud.speech.v2.BatchRecognizeResponse;
import com.google.cloud.speech.v2.GcsOutputConfig;
import com.google.cloud.speech.v2.RecognitionConfig;
import com.google.cloud.speech.v2.RecognitionFeatures;
import com.google.cloud.speech.v2.RecognitionOutputConfig;
import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechRecognitionResult;
import com.google.cloud.speech.v2.SpeechSettings;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TranscribeGcpServiceImpl implements TranscribeGcpService {

    private final S3Client s3;
    private final Storage gcs; // Stage S3 -> GCS cho batch
    private final TrackRepository trackRepository;
    private final ApplicationEventPublisher publisher;
    private final GcpProperties props;
    private final GoogleCredentials googleCredentials;
    private final SpeechClient speechV2;

    private final ObjectMapper json = new ObjectMapper();

    public TranscribeGcpServiceImpl(
            S3Client s3,
            Storage gcs,
            TrackRepository trackRepository,
            ApplicationEventPublisher publisher,
            GcpProperties props,
            GoogleCredentials googleCredentials,
            @Qualifier("speechV2Client") SpeechClient speechV2
    ) {
        this.s3 = s3;
        this.gcs = gcs;
        this.trackRepository = trackRepository;
        this.publisher = publisher;
        this.props = props;
        this.googleCredentials = googleCredentials;
        this.speechV2 = speechV2;
    }

    @Value("${aws.s3.bucket-name:${S3_BUCKET_NAME:}}")
    private String mediaBucket;

    @Override
    @Async("taskExecutor")
    public void startAndPollTranscription(Long trackId) {
        InspirationTrack track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        if (!StringUtils.hasText(mediaBucket) || !StringUtils.hasText(track.getS3Key())) {
            markFailed(track, "Missing bucket or s3Key");
            return;
        }

        try {
            byte[] audioBytes = readAllBytesFromS3(mediaBucket, normalizeKey(track.getS3Key()));

            String lang = Optional.ofNullable(props.getSpeech().getLanguageCode()).orElse("vi-VN");
            String model = Optional.ofNullable(props.getSpeech().getModel()).orElse("latest_long");
            String location = Optional.ofNullable(props.getSpeech().getLocation()).orElse("global");

            String lyrics;
            try {
                // <=60s
                lyrics = doRecognize(speechV2, location, lang, model, audioBytes);
            } catch (InvalidArgumentException ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : "";

                // >60s → batch
                if (msg.contains("maximum of 60 seconds")) {
                    log.warn("[STT v2] Over 60s -> fallback to batchRecognize");
                    String gcsInputUri = stageS3ToGcs(track);
                    lyrics = runBatchRecognize(location, lang, model, gcsInputUri);
                    cleanupStagingIfConfigured(gcsInputUri);
                }
                // language/model không support ở region → global
                else if (!"global".equalsIgnoreCase(location)
                        && msg.contains("is not supported") && msg.contains("in the location")) {
                    log.warn("[STT v2] {} -> fallback location=global", msg);
                    try (SpeechClient globalClient = buildSpeechClient("global")) {
                        lyrics = doRecognize(globalClient, "global", lang, model, audioBytes);
                    }
                }
                // model không support → bỏ model
                else if (msg.contains("is not supported") && msg.contains("model")) {
                    log.warn("[STT v2] {} -> fallback drop model (default)", msg);
                    try (SpeechClient client = "global".equalsIgnoreCase(location) ? null : buildSpeechClient(location)) {
                        SpeechClient c = client != null ? client : speechV2;
                        lyrics = doRecognize(c, location, lang, null, audioBytes);
                    }
                } else {
                    throw ex;
                }
            }

            track.setLyricsText(lyrics);
            track.setStatus(TrackStatus.TRANSCRIBED);
            trackRepository.save(track);

            publisher.publishEvent(new LyricsExtractedEvent(track.getId(), lyrics));
            log.info("[STT v2] Completed, chars={}", lyrics.length());

        } catch (Exception e) {
            log.error("[STT v2] Failed: {}", e.getMessage(), e);
            markFailed(track, "STT v2 failed: " + e.getMessage());
        }
    }

    /* ================= recognize (≤60s) ================= */

    private String doRecognize(
            SpeechClient client,
            String location,
            String lang,
            String model,
            byte[] audioBytes
    ) {
        RecognitionFeatures features = RecognitionFeatures.newBuilder()
                .setEnableAutomaticPunctuation(Boolean.TRUE.equals(props.getSpeech().getEnablePunctuation()))
                .build();

        RecognitionConfig.Builder cfg = RecognitionConfig.newBuilder()
                .setAutoDecodingConfig(AutoDetectDecodingConfig.newBuilder().build())
                .addLanguageCodes(lang)
                .setFeatures(features);

        if (StringUtils.hasText(model)) {
            cfg.setModel(model);
        }

        String recognizer = String.format("projects/%s/locations/%s/recognizers/_",
                props.getProjectId(), location);

        RecognizeRequest req = RecognizeRequest.newBuilder()
                .setRecognizer(recognizer)
                .setConfig(cfg.build())
                .setContent(ByteString.copyFrom(audioBytes))
                .build();

        log.info("[STT v2] Recognize start (model={}, lang={}, location={}, bytes={}, at={})",
                (StringUtils.hasText(model) ? model : "default"), lang, location, audioBytes.length, Instant.now());

        RecognizeResponse resp = client.recognize(req);

        StringBuilder transcript = new StringBuilder();
        for (SpeechRecognitionResult r : resp.getResultsList()) {
            if (r.getAlternativesCount() > 0) {
                transcript.append(r.getAlternatives(0).getTranscript()).append("\n");
            }
        }
        return transcript.toString().trim();
    }

    /* ================= batchRecognize (>60s) ================= */

    private String runBatchRecognize(String location, String lang, String model, String gcsInputUri) throws Exception {
        boolean created = false;
        SpeechClient client = speechV2;
        if (!"global".equalsIgnoreCase(location)) {
            client = buildSpeechClient(location);
            created = true;
        }

        try {
            RecognitionFeatures features = RecognitionFeatures.newBuilder()
                    .setEnableAutomaticPunctuation(Boolean.TRUE.equals(props.getSpeech().getEnablePunctuation()))
                    .build();

            String safeLang = (lang != null && !lang.isBlank()) ? lang : "vi-VN";

            RecognitionConfig.Builder cfg = RecognitionConfig.newBuilder()
                    .setAutoDecodingConfig(AutoDetectDecodingConfig.newBuilder().build())
                    .addLanguageCodes(safeLang)
                    .setFeatures(features);

            if (StringUtils.hasText(model)) {
                cfg.setModel(model);
            }

            String recognizer = String.format("projects/%s/locations/%s/recognizers/_",
                    props.getProjectId(), location);

            String outBucket = Optional.ofNullable(props.getSpeech().getGcsOutputBucket()).orElse("");
            String outPrefix = Optional.ofNullable(props.getSpeech().getGcsOutputPrefix()).orElse("pwb/stt/");
            String outUri = StringUtils.hasText(outBucket) ? ("gs://" + outBucket + "/" + outPrefix) : "";

            // config ở cấp Request
            BatchRecognizeRequest.Builder req = BatchRecognizeRequest.newBuilder()
                    .setRecognizer(recognizer)
                    .setConfig(cfg.build());

            // files chỉ cần uri
            BatchRecognizeFileMetadata meta = BatchRecognizeFileMetadata.newBuilder()
                    .setUri(gcsInputUri)
                    .build();
            req.addFiles(meta);

            if (StringUtils.hasText(outUri)) {
                RecognitionOutputConfig roc = RecognitionOutputConfig.newBuilder()
                        .setGcsOutputConfig(GcsOutputConfig.newBuilder().setUri(outUri).build())
                        .build();
                req.setRecognitionOutputConfig(roc);
            }

            log.info("[STT v2] BatchRecognize start (model={}, lang={}, location={}, in={}, out={})",
                    (StringUtils.hasText(model) ? model : "default"), safeLang, location, gcsInputUri, outUri);

            var op = client.batchRecognizeAsync(req.build());
            int timeout = Math.max(60, props.getSpeech().getTimeoutSeconds());
            BatchRecognizeResponse resp = op.get(timeout, TimeUnit.SECONDS);

            // 1) thử lấy inline
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, BatchRecognizeFileResult> e : resp.getResultsMap().entrySet()) {
                BatchRecognizeFileResult fileResult = e.getValue();
                List<?> results = tryGetResultList(fileResult); // reflection-safe
                if (results == null) continue;
                for (Object r : results) {
                    String t = tryExtractTranscript(r);
                    if (t != null && !t.isBlank()) sb.append(t).append('\n');
                }
            }
            String text = sb.toString().trim();

            // 2) nếu inline rỗng & có output -> đọc JSON từ GCS
            if (text.isEmpty() && StringUtils.hasText(outUri)) {
                String noScheme = outUri.replaceFirst("^gs://", "");
                int slash = noScheme.indexOf('/');
                String outBkt = (slash > 0) ? noScheme.substring(0, slash) : noScheme;
                String outPfx = (slash > 0) ? noScheme.substring(slash + 1) : "";
                text = readTranscriptFromGcsOutput(outBkt, outPfx, 10 * 60 * 1000L);
                if (!text.isBlank()) {
                    log.info("[STT v2] Read transcript from GCS ({}...): {} chars", outUri, text.length());
                } else {
                    log.info("[STT v2] Transcript written to {} (no inline, not parsed yet)", outUri);
                }
            }
            return text;
        } finally {
            if (created && client != null) {
                try { client.close(); } catch (Exception ignore) {}
            }
        }
    }

    /* ---------- Helpers cho khác biệt API/phiên bản ---------- */

    @SuppressWarnings("unchecked")
    private List<?> tryGetResultList(BatchRecognizeFileResult fileResult) {
        Object list = tryCall(fileResult, "getResultsList");
        if (list instanceof List<?>) return (List<?>) list;

        list = tryCall(fileResult, "getTranscriptionsList");
        if (list instanceof List<?>) return (List<?>) list;

        list = tryCall(fileResult, "getTranscriptionList");
        if (list instanceof List<?>) return (List<?>) list;

        log.warn("[STT v2] BatchRecognizeFileResult has no *ResultsList/*TranscriptionsList in this version.");
        return null;
    }

    private String tryExtractTranscript(Object resultObj) {
        try {
            if (resultObj instanceof SpeechRecognitionResult r) {
                if (r.getAlternativesCount() > 0) {
                    return r.getAlternatives(0).getTranscript();
                }
                return null;
            }
            Object alts = tryCall(resultObj, "getAlternativesList");
            if (alts instanceof List<?>) {
                List<?> alternatives = (List<?>) alts;
                if (!alternatives.isEmpty()) {
                    Object alt0 = alternatives.get(0);
                    Object t = tryCall(alt0, "getTranscript");
                    return t != null ? String.valueOf(t) : null;
                }
            }
            Object t = tryCall(resultObj, "getTranscript");
            return t != null ? String.valueOf(t) : null;
        } catch (Exception ex) {
            log.debug("[STT v2] tryExtractTranscript reflection failed: {}", ex.toString());
            return null;
        }
    }

    private Object tryCall(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Exception e) {
            log.debug("[STT v2] Reflection call {}.{} failed: {}", target.getClass().getSimpleName(), method, e.toString());
            return null;
        }
    }

    /* ---------- Đọc transcript JSON từ GCS (khi không có inline) ---------- */

    private String readTranscriptFromGcsOutput(String bucket, String prefixBase, long lookbackMillis) {
        long now = System.currentTimeMillis();
        try {
            String prefix = prefixBase.endsWith("/") ? prefixBase : (prefixBase + "/");

            Iterable<Blob> blobs = gcs.list(bucket, Storage.BlobListOption.prefix(prefix)).iterateAll();
            List<Blob> candidates = new ArrayList<>();
            for (Blob b : blobs) {
                if (!b.getName().endsWith(".json")) continue;
                Long upd = b.getUpdateTime();
                if (upd != null && upd >= now - lookbackMillis) candidates.add(b);
            }
            candidates.sort((a, b) -> Objects.compare(b.getUpdateTime(), a.getUpdateTime(), Long::compare));

            for (Blob blob : candidates) {
                byte[] content = blob.getContent();
                String jsonStr = new String(content, StandardCharsets.UTF_8);
                String tx = extractTranscriptsFromJson(jsonStr);
                if (!tx.isBlank()) return tx;
            }
        } catch (Exception ex) {
            log.warn("[STT v2] Read GCS output failed: {}", ex.toString());
        }
        return "";
    }

    private String extractTranscriptsFromJson(String jsonStr) throws Exception {
        JsonNode root = json.readTree(jsonStr);
        List<String> lines = new ArrayList<>();
        collectTranscriptNodes(root, lines);
        return String.join("\n", lines).trim();
    }

    private void collectTranscriptNodes(JsonNode node, List<String> out) {
        if (node == null) return;
        if (node.isObject()) {
            JsonNode tr = node.get("transcript");
            if (tr != null && tr.isTextual()) {
                String t = tr.asText("");
                if (!t.isBlank()) out.add(t);
            }
            var fields = node.fields();
            while (fields.hasNext()) collectTranscriptNodes(fields.next().getValue(), out);
        } else if (node.isArray()) {
            for (JsonNode child : node) collectTranscriptNodes(child, out);
        }
    }

    /* ================= staging S3 -> GCS ================= */

    private String stageS3ToGcs(InspirationTrack t) throws Exception {
        String gcsBucket = props.getGcs().getBucket();
        if (!StringUtils.hasText(gcsBucket)) {
            throw new IllegalStateException("GCS bucket (staging) is not configured");
        }
        String safeName = (t.getFileName() == null || t.getFileName().isBlank()) ? "audio" : t.getFileName();
        String object = "pwb/inspiration/" + t.getProject().getId() + "/" + t.getId()
                + "/" + Instant.now().toEpochMilli() + "-" + URLEncoder.encode(safeName, StandardCharsets.UTF_8);
        String gcsUri = "gs://" + gcsBucket + "/" + object;

        try (ResponseInputStream<?> in = s3.getObject(
                GetObjectRequest.builder().bucket(mediaBucket).key(normalizeKey(t.getS3Key())).build());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);

            BlobInfo blob = BlobInfo.newBuilder(BlobId.of(gcsBucket, object))
                    .setContentType(guessMimeFromName(t.getFileName()))
                    .build();
            gcs.create(blob, out.toByteArray());
        }
        log.info("[GCS] Staged input: {}", gcsUri);
        return gcsUri;
    }

    private void cleanupStagingIfConfigured(String gcsUri) {
        if (!props.getGcs().isDeleteAfterFinish()) return;
        try {
            String noScheme = gcsUri.replaceFirst("^gs://", "");
            int slash = noScheme.indexOf('/');
            String bucket = noScheme.substring(0, slash);
            String name = noScheme.substring(slash + 1);
            boolean ok = gcs.delete(BlobId.of(bucket, name));
            log.info("[GCS] Deleted staging {} -> {}", gcsUri, ok);
        } catch (Exception ex) {
            log.warn("[GCS] Delete staging failed {}: {}", gcsUri, ex.getMessage());
        }
    }

    /* ================= util ================= */

    private SpeechClient buildSpeechClient(String location) throws Exception {
        String endpoint = "global".equalsIgnoreCase(location)
                ? "speech.googleapis.com:443"
                : location + "-speech.googleapis.com:443";
        SpeechSettings settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
                .setEndpoint(endpoint)
                .build();
        return SpeechClient.create(settings);
    }

    private void markFailed(InspirationTrack track, String reason) {
        track.setStatus(TrackStatus.FAILED);
        trackRepository.save(track);
        log.warn("[Transcribe v2] FAILED trackId={}, reason={}", track.getId(), reason);
    }

    private String normalizeKey(String key) { return key == null ? "" : key.replaceFirst("^/+", ""); }

    private String guessMimeFromName(String filename) {
        if (filename == null) return "application/octet-stream";
        String f = filename.toLowerCase();
        if (f.endsWith(".wav")) return "audio/wav";
        if (f.endsWith(".mp3")) return "audio/mpeg";
        if (f.endsWith(".m4a")) return "audio/mp4";
        if (f.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream";
    }

    private byte[] readAllBytesFromS3(String bucket, String key) throws Exception {
        try (ResponseInputStream<?> in = s3.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }
}
