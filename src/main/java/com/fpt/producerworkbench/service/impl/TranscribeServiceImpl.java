package com.fpt.producerworkbench.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.common.TrackStatus;
import com.fpt.producerworkbench.dto.event.LyricsExtractedEvent;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.TrackRepository;
import com.fpt.producerworkbench.service.TranscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLocationResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranscribeServiceImpl implements TranscribeService {

    private final TranscribeClient transcribeClient;
    private final S3Client s3;
    private final TrackRepository trackRepository;
    private final ApplicationEventPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.create();

    @Value("${aws.region:${AWS_REGION:unknown}}")
    private String awsRegion;

    @Value("${aws.s3.bucket-name:${S3_BUCKET_NAME:}}")
    private String mediaBucket;

    private final LanguageCode languageCode = LanguageCode.VI_VN;
    private final Duration pollInterval = Duration.ofSeconds(5);
    private final Duration timeout = Duration.ofMinutes(20);

    @Override
    @Async
    public void startAndPollTranscription(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        if (!StringUtils.hasText(mediaBucket)) {
            log.error("[Transcribe] aws.s3.bucketName chưa được cấu hình!");
            markFailed(track, "aws.s3.bucketName is not configured");
            return;
        }
        if (!StringUtils.hasText(track.getS3Key())) {
            log.error("[Transcribe] trackId={} không có s3Key", trackId);
            markFailed(track, "Track missing S3 key");
            return;
        }

        String key = normalizeKey(track.getS3Key());
        if (!exists(mediaBucket, key)) {
            markFailed(track, "S3 object not found: s3://" + mediaBucket + "/" + key);
            return;
        }

        String bucketRegion = getBucketRegion(mediaBucket);
        log.info("[Transcribe] AppRegion={}, BucketRegion={}", awsRegion, bucketRegion);

        String jobName = "pwb-" + track.getId() + "-" + UUID.randomUUID();
        String mediaUri = "s3://" + mediaBucket + "/" + key;
        MediaFormat format = guessFormat(track.getFileName());

        log.info("[Transcribe] Start job={} for trackId={}, language={}, mediaUri={}, format={}",
                jobName, trackId, languageCode, mediaUri, format);

        track.setStatus(TrackStatus.TRANSCRIBING);
        track.setTranscribeJobName(jobName);
        trackRepository.save(track);

        try {
            StartTranscriptionJobRequest startReq = StartTranscriptionJobRequest.builder()
                    .transcriptionJobName(jobName)
                    .languageCode(languageCode)
                    .mediaFormat(format)
                    .media(Media.builder().mediaFileUri(mediaUri).build())
                    .build();

            StartTranscriptionJobResponse startResp = transcribeClient.startTranscriptionJob(startReq);
            log.info("[Transcribe] Start job ok: {}", startResp.transcriptionJob().transcriptionJobName());
        } catch (Exception e) {
            log.error("[Transcribe] Start job failed: {}", e.getMessage(), e);
            markFailed(track, "StartTranscriptionJob error: " + e.getMessage());
            return;
        }

        long startAt = System.currentTimeMillis();
        while (true) {
            try {
                GetTranscriptionJobResponse resp = transcribeClient.getTranscriptionJob(
                        GetTranscriptionJobRequest.builder().transcriptionJobName(jobName).build());

                TranscriptionJobStatus status = resp.transcriptionJob().transcriptionJobStatus();
                switch (status) {
                    case COMPLETED -> {
                        String transcriptUri = Optional.ofNullable(resp.transcriptionJob())
                                .map(j -> j.transcript())
                                .map(Transcript::transcriptFileUri)
                                .orElse(null);

                        log.info("[Transcribe] Completed job={}, transcriptUri={}", jobName, transcriptUri);
                        String lyrics = fetchTranscriptText(transcriptUri);

                        track.setLyricsText(lyrics);
                        track.setStatus(TrackStatus.TRANSCRIBED);
                        trackRepository.save(track);

                        publisher.publishEvent(new LyricsExtractedEvent(track.getId(), lyrics));
                        return;
                    }
                    case FAILED -> {
                        String reason = resp.transcriptionJob().failureReason();
                        log.error("[Transcribe] Job {} failed: {}", jobName, reason);
                        markFailed(track, "Transcription failed: " + reason);
                        return;
                    }
                    default -> {
                        if (System.currentTimeMillis() - startAt > timeout.toMillis()) {
                            log.error("[Transcribe] Job {} timeout", jobName);
                            markFailed(track, "Transcription timeout");
                            return;
                        }
                        try { Thread.sleep(pollInterval.toMillis()); } catch (InterruptedException ignored) {}
                    }
                }
            } catch (Exception e) {
                log.error("[Transcribe] Poll error for job {}: {}", jobName, e.getMessage(), e);
                markFailed(track, "GetTranscriptionJob error: " + e.getMessage());
                return;
            }
        }
    }


    private String normalizeKey(String key) {
        return key == null ? "" : key.replaceFirst("^/+", "");
    }

    private boolean exists(String bucket, String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception e) {
            log.error("[Transcribe] S3 headObject failed for s3://{}/{} -> {}",
                    bucket, key,
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
            return false;
        }
    }

    private String getBucketRegion(String bucket) {
        try {
            GetBucketLocationResponse r = s3.getBucketLocation(
                    GetBucketLocationRequest.builder().bucket(bucket).build());
            String lc = r.locationConstraintAsString();
            return (lc == null || lc.isBlank()) ? "us-east-1" : lc;
        } catch (Exception e) {
            log.warn("[Transcribe] Cannot resolve bucket region for {}: {}", bucket, e.getMessage());
            return "unknown";
        }
    }

    private void markFailed(Track track, String reason) {
        track.setStatus(TrackStatus.FAILED);
        trackRepository.save(track);
        log.warn("[Transcribe] Mark FAILED for trackId={}, reason={}", track.getId(), reason);
    }

    private MediaFormat guessFormat(String fileName) {
        String lower = (fileName == null) ? "" : fileName.toLowerCase();
        if (lower.endsWith(".wav")) return MediaFormat.WAV;
        if (lower.endsWith(".mp3")) return MediaFormat.MP3;
        if (lower.endsWith(".mp4") || lower.endsWith(".m4a")) return MediaFormat.MP4;
        return MediaFormat.MP3;
    }

    private String fetchTranscriptText(String transcriptUri) {
        if (!StringUtils.hasText(transcriptUri)) {
            log.error("[Transcribe] transcriptUri is empty");
            return "";
        }
        try {
            String json = webClient.get()
                    .uri(URI.create(transcriptUri))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(json);
            JsonNode transcripts = root.path("results").path("transcripts");
            if (transcripts.isArray() && transcripts.size() > 0) {
                return transcripts.get(0).path("transcript").asText("");
            }
            log.warn("[Transcribe] transcripts array empty in JSON");
            return "";
        } catch (Exception e) {
            log.error("[Transcribe] Parse transcript JSON failed: {}", e.getMessage(), e);
            return "";
        }
    }
}


