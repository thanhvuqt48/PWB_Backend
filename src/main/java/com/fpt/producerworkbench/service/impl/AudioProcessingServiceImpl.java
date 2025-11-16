package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProcessingStatus;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.TrackRepository;
import com.fpt.producerworkbench.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Implementation của AudioProcessingService sử dụng:
 * - Google Cloud Text-to-Speech cho TTS tiếng Việt (thay AWS Polly)
 * - FFmpeg cho mixing voice tag và convert HLS
 * - ffprobe cho lấy duration
 * - AWS S3 cho storage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AudioProcessingServiceImpl implements AudioProcessingService {

    private final TrackRepository trackRepository;
    private final FileKeyGenerator fileKeyGenerator;
    private final VoiceTagTtsService voiceTagTtsService;
    private final FFmpegService ffmpegService;
    private final FileStorageService fileStorageService;

    @Value("${storage.base-dir:/var/pwb-files}")
    private String storageBaseDir;

    @Value("${ffmpeg.voice-tag.interval-seconds:25}")
    private int voiceTagIntervalSeconds;

    @Async
    @Transactional
    @Override
    public void processTrackAudio(Long trackId) {
        log.info("Bắt đầu xử lý audio cho track ID: {}", trackId);
        
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Track không tồn tại"));
        
        try {

            String audioForHls;

            if (Boolean.TRUE.equals(track.getVoiceTagEnabled()) && track.getVoiceTagText() != null) {
                log.info("Track {} bật voice tag. Bắt đầu TTS và mixing...", trackId);
                
                String voiceTagKey = generateVoiceTagAudio(track.getVoiceTagText(), trackId);
                track.setVoiceTagAudioKey(voiceTagKey);
                
                // Trộn voice tag vào master
                audioForHls = mixVoiceTagIntoAudio(track.getS3OriginalKey(), voiceTagKey, trackId);
            } else {
                log.info("Track {} không bật voice tag. Dùng trực tiếp master.", trackId);
                audioForHls = track.getS3OriginalKey();
            }

            String hlsPrefix = convertToHls(audioForHls, trackId);
            track.setHlsPrefix(hlsPrefix);

            Integer duration = getAudioDuration(track.getS3OriginalKey());
            track.setDuration(duration);

            track.setProcessingStatus(ProcessingStatus.READY);
            track.setErrorMessage(null);
            trackRepository.save(track);

            log.info("Hoàn thành xử lý audio cho track ID: {}", trackId);

        } catch (Exception e) {
            log.error("Lỗi khi xử lý audio cho track ID {}: {}", trackId, e.getMessage(), e);
            
            track.setProcessingStatus(ProcessingStatus.FAILED);
            track.setErrorMessage("Lỗi xử lý audio: " + e.getMessage());
            trackRepository.save(track);
        }
    }

    @Override
    public String generateVoiceTagAudio(String text, Long trackId) {
        log.info("Tạo voice tag audio cho track {} bằng Google Cloud TTS (tiếng Việt)", trackId);
        log.info("Text: {}", text);

        File tempFile = null;
        try {
            tempFile = createTempFile("voice-tag-", ".mp3");
            
            try (InputStream audioStream = voiceTagTtsService.synthesizeVoiceTag(text);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                audioStream.transferTo(fos);
            }

            log.info("Google TTS synthesized audio, size: {} bytes", tempFile.length());

            String voiceTagKey = fileKeyGenerator.generateTrackVoiceTagKey(trackId);
            fileStorageService.uploadFile(tempFile, voiceTagKey, "audio/mpeg");

            log.info("Voice tag uploaded to S3: {}", voiceTagKey);
            return voiceTagKey;

        } catch (Exception e) {
            log.error("Lỗi khi tạo voice tag cho track {}: {}", trackId, e.getMessage(), e);
            throw new RuntimeException("Không thể tạo voice tag audio", e);
        } finally {
            cleanupTempFile(tempFile);
        }
    }

    @Override
    public String mixVoiceTagIntoAudio(String masterS3Key, String voiceTagS3Key, Long trackId) {
        log.info("Trộn voice tag vào master audio bằng FFmpeg");
        log.info("Master key: {}, Voice tag key: {}", masterS3Key, voiceTagS3Key);

        File masterFile = null;
        File voiceTagFile = null;
        File mixedFile = null;

        try {
            masterFile = createTempFile("master-", getFileExtension(masterS3Key));
            voiceTagFile = createTempFile("voice-tag-", ".mp3");

            fileStorageService.downloadFile(masterS3Key, masterFile);
            fileStorageService.downloadFile(voiceTagS3Key, voiceTagFile);

            log.info("Downloaded master ({} bytes) và voice tag ({} bytes)",
                    masterFile.length(), voiceTagFile.length());

            mixedFile = createTempFile("mixed-", ".m4a");
            ffmpegService.mixVoiceTagIntoAudio(masterFile, voiceTagFile, mixedFile, voiceTagIntervalSeconds);

            log.info("FFmpeg mixed audio successfully, size: {} bytes", mixedFile.length());

            String mixedKey = String.format("audio/mixed/%d/mixed.m4a", trackId);
            fileStorageService.uploadFile(mixedFile, mixedKey, "audio/mp4");

            log.info("Mixed audio uploaded to S3: {}", mixedKey);
            return mixedKey;

        } catch (Exception e) {
            log.error("Lỗi khi mix voice tag cho track {}: {}", trackId, e.getMessage(), e);
            throw new RuntimeException("Không thể mix voice tag vào audio", e);
        } finally {
            cleanupTempFile(masterFile);
            cleanupTempFile(voiceTagFile);
            cleanupTempFile(mixedFile);
        }
    }

    @Override
    public String convertToHls(String audioS3Key, Long trackId) {
        log.info("Convert audio sang HLS bằng FFmpeg. Audio key: {}", audioS3Key);

        File inputFile = null;
        File hlsDir = null;

        try {
            inputFile = createTempFile("input-", getFileExtension(audioS3Key));
            fileStorageService.downloadFile(audioS3Key, inputFile);

            log.info("Downloaded audio for HLS conversion, size: {} bytes", inputFile.length());

            hlsDir = Files.createTempDirectory("hls-" + trackId + "-").toFile();

            ffmpegService.convertToHLS(inputFile, hlsDir, 10);

            log.info("FFmpeg converted to HLS successfully. Output dir: {}", hlsDir.getAbsolutePath());

            String hlsPrefix = fileKeyGenerator.generateTrackHlsPrefix(trackId);
            uploadHlsDirectory(hlsDir, hlsPrefix);

            log.info("HLS files uploaded to S3 with prefix: {}", hlsPrefix);
            return hlsPrefix;

        } catch (Exception e) {
            log.error("Lỗi khi convert HLS cho track {}: {}", trackId, e.getMessage(), e);
            throw new RuntimeException("Không thể convert audio sang HLS", e);
        } finally {
            cleanupTempFile(inputFile);
            cleanupTempDirectory(hlsDir);
        }
    }

    @Override
    public Integer getAudioDuration(String audioS3Key) {
        log.info("Lấy duration của audio bằng ffprobe. Audio key: {}", audioS3Key);

        File audioFile = null;
        try {
            audioFile = createTempFile("duration-check-", getFileExtension(audioS3Key));
            fileStorageService.downloadFile(audioS3Key, audioFile);

            log.info("Downloaded audio for duration check, size: {} bytes", audioFile.length());

            int duration = ffmpegService.getAudioDuration(audioFile);

            log.info("Audio duration: {} giây", duration);
            return duration;

        } catch (Exception e) {
            log.error("Lỗi khi lấy duration của audio {}: {}", audioS3Key, e.getMessage(), e);
            throw new RuntimeException("Không thể lấy duration của audio", e);
        } finally {
            cleanupTempFile(audioFile);
        }
    }

    // ========== Helper Methods ==========

    /**
     * Tạo file tạm trong thư mục storage
     */
    private File createTempFile(String prefix, String suffix) throws Exception {
        File tempDir = new File(storageBaseDir, "temp");
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new RuntimeException("Không thể tạo temp directory: " + tempDir.getAbsolutePath());
        }
        
        String uniqueName = prefix + UUID.randomUUID() + suffix;
        return new File(tempDir, uniqueName);
    }

    /**
     * Xóa file tạm
     */
    private void cleanupTempFile(File file) {
        if (file != null && file.exists()) {
            try {
                Files.delete(file.toPath());
                log.debug("Deleted temp file: {}", file.getAbsolutePath());
            } catch (Exception e) {
                log.warn("Không thể xóa temp file {}: {}", file.getAbsolutePath(), e.getMessage());
            }
        }
    }

    /**
     * Xóa thư mục tạm và tất cả files bên trong
     */
    private void cleanupTempDirectory(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            try {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        Files.delete(file.toPath());
                    }
                }
                Files.delete(dir.toPath());
                log.debug("Deleted temp directory: {}", dir.getAbsolutePath());
            } catch (Exception e) {
                log.warn("Không thể xóa temp directory {}: {}", dir.getAbsolutePath(), e.getMessage());
            }
        }
    }

    /**
     * Lấy extension từ S3 key
     */
    private String getFileExtension(String s3Key) {
        int lastDot = s3Key.lastIndexOf('.');
        if (lastDot > 0 && lastDot < s3Key.length() - 1) {
            return s3Key.substring(lastDot);
        }
        return ".tmp";
    }

    /**
     * Upload tất cả files trong HLS directory lên S3
     */
    private void uploadHlsDirectory(File hlsDir, String s3Prefix) throws Exception {
        File[] files = hlsDir.listFiles();
        if (files == null || files.length == 0) {
            throw new RuntimeException("HLS directory rỗng: " + hlsDir.getAbsolutePath());
        }

        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName();
                String s3Key = s3Prefix + fileName;
                
                String contentType;
                if (fileName.endsWith(".m3u8")) {
                    contentType = "application/vnd.apple.mpegurl";
                } else if (fileName.endsWith(".ts")) {
                    contentType = "video/mp2t";
                } else {
                    contentType = "application/octet-stream";
                }

                fileStorageService.uploadFile(file, s3Key, contentType);
                log.debug("Uploaded HLS file: {} -> {}", fileName, s3Key);
            }
        }

        log.info("Uploaded {} HLS files to S3", files.length);
    }
}
