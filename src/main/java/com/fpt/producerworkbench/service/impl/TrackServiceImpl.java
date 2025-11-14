//package com.fpt.producerworkbench.service.impl;
//
//import com.fpt.producerworkbench.common.TrackStatus;
//import com.fpt.producerworkbench.dto.event.LyricsExtractedEvent;
//import com.fpt.producerworkbench.dto.request.TrackUploadCompleteRequest;
//import com.fpt.producerworkbench.dto.request.TrackUploadUrlRequest;
//import com.fpt.producerworkbench.dto.response.TrackSuggestionResponse;
//import com.fpt.producerworkbench.dto.response.TrackUploadUrlResponse;
//import com.fpt.producerworkbench.entity.Project;
//import com.fpt.producerworkbench.entity.Track;
//import com.fpt.producerworkbench.entity.User;
//import com.fpt.producerworkbench.dto.event.AudioUploadedEvent;
//import com.fpt.producerworkbench.exception.AppException;
//import com.fpt.producerworkbench.exception.ErrorCode;
//import com.fpt.producerworkbench.repository.ProjectMemberRepository;
//import com.fpt.producerworkbench.repository.ProjectRepository;
//import com.fpt.producerworkbench.repository.TrackRepository;
//import com.fpt.producerworkbench.repository.UserRepository;
//import com.fpt.producerworkbench.service.FileKeyGenerator;
//import com.fpt.producerworkbench.service.FileStorageService;
//import com.fpt.producerworkbench.service.TrackService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.ApplicationEventPublisher;
//import org.springframework.stereotype.Service;
//import org.springframework.beans.factory.annotation.Value;
//import software.amazon.awssdk.services.s3.S3Client;
//import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
//
//import java.time.Duration;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class TrackServiceImpl implements TrackService {
//
//    private final FileKeyGenerator keyGen;
//    private final FileStorageService storage;
//    private final ProjectRepository projectRepo;
//    private final UserRepository userRepo;
//    private final ProjectMemberRepository projectMemberRepo;
//    private final TrackRepository trackRepo;
//    private final ApplicationEventPublisher publisher;
//    private final S3Client s3;
//    @Value("${aws.s3.bucket-name:${S3_BUCKET_NAME:}}")
//    private String mediaBucket;
//
//    @Override
//    public TrackUploadUrlResponse generateUploadUrl(Long userId, TrackUploadUrlRequest req) {
//        Project p = projectRepo.findById(req.getProjectId())
//                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));
//        User u = userRepo.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
//
//        ensureMember(p.getId(), userId);
//
//        String objectKey = keyGen.generateTrackAudioKey(p.getId(), req.getFileName());
//        String url = storage.generatePresignedUploadUrl(objectKey, req.getMimeType(), Duration.ofMinutes(15));
//
//        return TrackUploadUrlResponse.builder()
//                .objectKey(objectKey)
//                .presignedPutUrl(url)
//                .expiresInSeconds(15L * 60L)
//                .build();
//    }
//
//    @Override
//    public Long uploadComplete(Long userId, TrackUploadCompleteRequest req) {
//        Project p = projectRepo.findById(req.getProjectId())
//                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));
//        User u = userRepo.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
//        ensureMember(p.getId(), userId);
//
//        Track track = trackRepo.save(Track.builder()
//                .project(p)
//                .uploader(u)
//                .fileName(req.getFileName())
//                .mimeType(req.getMimeType())
//                .sizeBytes(req.getSizeBytes())
//                .s3Key(req.getObjectKey())
//                .status(TrackStatus.TRANSCRIBING)
//                .build());
//
//        publisher.publishEvent(new AudioUploadedEvent(track.getId()));
//        return track.getId();
//    }
//
//    @Override
//    public TrackSuggestionResponse getSuggestion(Long userId, Long trackId) {
//        Track t = trackRepo.findById(trackId)
//                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
//        ensureMember(t.getProject().getId(), userId);
//
//        return TrackSuggestionResponse.builder()
//                .trackId(t.getId())
//                .status(t.getStatus())
//                .lyricsText(t.getLyricsText())
//                .aiSuggestions(t.getAiSuggestions())
//                .transcribeJobName(t.getTranscribeJobName())
//                .build();
//    }
//
//    @Override
//    public void resuggest(Long userId, Long trackId) {
//        Track t = trackRepo.findById(trackId)
//                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
//        ensureMember(t.getProject().getId(), userId);
//
//        if (t.getLyricsText() == null || t.getLyricsText().isBlank()) {
//            throw new AppException(ErrorCode.BAD_REQUEST);
//        }
//        if (t.getStatus() == TrackStatus.TRANSCRIBING || t.getStatus() == TrackStatus.SUGGESTING) {
//            throw new AppException(ErrorCode.CONFLICT);
//        }
//
//        t.setStatus(TrackStatus.SUGGESTING);
//        t.setAiSuggestions(null);
//        trackRepo.save(t);
//
//        publisher.publishEvent(new LyricsExtractedEvent(t.getId(), t.getLyricsText()));
//        log.info("[Track] Re-suggest triggered for track {}", t.getId());
//    }
//
//    @Override
//    public void deleteTrack(Long userId, Long trackId) {
//        Track t = trackRepo.findById(trackId)
//                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
//        ensureMember(t.getProject().getId(), userId);
//
//        try {
//            if (mediaBucket != null && !mediaBucket.isBlank()
//                    && t.getS3Key() != null && !t.getS3Key().isBlank()) {
//                s3.deleteObject(DeleteObjectRequest.builder()
//                        .bucket(mediaBucket)
//                        .key(t.getS3Key().replaceFirst("^/+", ""))
//                        .build());
//                log.info("[Track] Deleted S3 object s3://{}/{}", mediaBucket, t.getS3Key());
//            }
//        } catch (Exception e) {
//            log.warn("[Track] Delete S3 object failed for {}: {}", t.getS3Key(), e.getMessage());
//        }
//
//        trackRepo.delete(t);
//        log.info("[Track] Deleted track {}", trackId);
//    }
//
//    private void ensureMember(Long projectId, Long userId) {
//        boolean ok = projectMemberRepo.existsByProject_IdAndUser_Id(projectId, userId);
//        if (!ok) throw new AppException(ErrorCode.NOT_PROJECT_MEMBER);
//    }
//}
package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.TrackStatus;
import com.fpt.producerworkbench.dto.event.LyricsExtractedEvent;
import com.fpt.producerworkbench.dto.request.TrackUploadCompleteRequest;
import com.fpt.producerworkbench.dto.request.TrackUploadUrlRequest;
import com.fpt.producerworkbench.dto.response.TrackSuggestionResponse;
import com.fpt.producerworkbench.dto.response.TrackUploadUrlResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.dto.event.AudioUploadedEvent;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.TrackRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.TrackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackServiceImpl implements TrackService {

    private final FileKeyGenerator keyGen;
    private final FileStorageService storage;
    private final ProjectRepository projectRepo;
    private final UserRepository userRepo;
    private final ProjectMemberRepository projectMemberRepo;
    private final TrackRepository trackRepo;
    private final ApplicationEventPublisher publisher;
    private final S3Client s3;

    @Value("${aws.s3.bucket-name:${S3_BUCKET_NAME:}}")
    private String mediaBucket;

    @Value("${aws.s3.max-upload-mb:25}")
    private long maxUploadMb;

    private long maxBytes() {
        return maxUploadMb * 1024L * 1024L;
    }

    @Override
    public TrackUploadUrlResponse generateUploadUrl(Long userId, TrackUploadUrlRequest req) {
        Project p = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));
        User u = userRepo.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ensureMember(p.getId(), userId);

        String objectKey = keyGen.generateTrackAudioKey(p.getId(), req.getFileName());
        String url = storage.generatePresignedUploadUrl(objectKey, req.getMimeType(), Duration.ofMinutes(15));

        return TrackUploadUrlResponse.builder()
                .objectKey(objectKey)
                .presignedPutUrl(url)
                .expiresInSeconds(15L * 60L)
                .build();
    }

    @Override
    public Long uploadComplete(Long userId, TrackUploadCompleteRequest req) {
        Project p = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));
        User u = userRepo.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        ensureMember(p.getId(), userId);

        if (mediaBucket == null || mediaBucket.isBlank()) {
            log.error("[Track] mediaBucket is not configured");
            throw new AppException(ErrorCode.FILE_STORAGE_NOT_FOUND);
        }

        String normalizedKey = normalizeKey(req.getObjectKey());
        long actualSize;
        try {
            var head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(mediaBucket)
                    .key(normalizedKey)
                    .build());
            actualSize = head.contentLength();
        } catch (S3Exception e) {
            log.error("[Track] headObject failed for s3://{}/{} -> {}", mediaBucket, normalizedKey,
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage());
            throw new AppException(ErrorCode.FILE_STORAGE_NOT_FOUND);
        }

        if (actualSize <= 0) {
            safeDeleteS3(normalizedKey);
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        if (actualSize > maxBytes()) {
            safeDeleteS3(normalizedKey);
            throw new AppException(ErrorCode.FILE_LARGE);
        }

        Track track = trackRepo.save(Track.builder()
                .project(p)
                .uploader(u)
                .fileName(req.getFileName())
                .mimeType(req.getMimeType())
                .sizeBytes(actualSize)
                .s3Key(normalizedKey)
                .status(TrackStatus.TRANSCRIBING)
                .build());

        publisher.publishEvent(new AudioUploadedEvent(track.getId()));
        return track.getId();
    }

    @Override
    public TrackSuggestionResponse getSuggestion(Long userId, Long trackId) {
        Track t = trackRepo.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        ensureMember(t.getProject().getId(), userId);

        return TrackSuggestionResponse.builder()
                .trackId(t.getId())
                .status(t.getStatus())
                .lyricsText(t.getLyricsText())
                .aiSuggestions(t.getAiSuggestions())
                .transcribeJobName(t.getTranscribeJobName())
                .build();
    }

    @Override
    public void resuggest(Long userId, Long trackId) {
        Track t = trackRepo.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        ensureMember(t.getProject().getId(), userId);

        if (t.getLyricsText() == null || t.getLyricsText().isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        if (t.getStatus() == TrackStatus.TRANSCRIBING || t.getStatus() == TrackStatus.SUGGESTING) {
            throw new AppException(ErrorCode.CONFLICT);
        }

        t.setStatus(TrackStatus.SUGGESTING);
        t.setAiSuggestions(null);
        trackRepo.save(t);

        publisher.publishEvent(new LyricsExtractedEvent(t.getId(), t.getLyricsText()));
        log.info("[Track] Re-suggest triggered for track {}", t.getId());
    }

    @Override
    public void deleteTrack(Long userId, Long trackId) {
        Track t = trackRepo.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        ensureMember(t.getProject().getId(), userId);

        try {
            if (mediaBucket != null && !mediaBucket.isBlank()
                    && t.getS3Key() != null && !t.getS3Key().isBlank()) {
                s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(mediaBucket)
                        .key(t.getS3Key().replaceFirst("^/+", ""))
                        .build());
                log.info("[Track] Deleted S3 object s3://{}/{}", mediaBucket, t.getS3Key());
            }
        } catch (Exception e) {
            log.warn("[Track] Delete S3 object failed for {}: {}", t.getS3Key(), e.getMessage());
        }

        trackRepo.delete(t);
        log.info("[Track] Deleted track {}", trackId);
    }

    private void ensureMember(Long projectId, Long userId) {
        boolean ok = projectMemberRepo.existsByProject_IdAndUser_Id(projectId, userId);
        if (!ok) throw new AppException(ErrorCode.NOT_PROJECT_MEMBER);
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.replaceFirst("^/+", "");
    }

    private void safeDeleteS3(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(mediaBucket)
                    .key(key)
                    .build());
            log.info("[Track] Deleted oversize/invalid S3 object s3://{}/{}", mediaBucket, key);
        } catch (Exception ex) {
            log.warn("[Track] Failed to delete S3 object s3://{}/{} -> {}", mediaBucket, key, ex.getMessage());
        }
    }
}
