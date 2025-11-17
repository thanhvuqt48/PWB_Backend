package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.MoneySplitStatus;
import com.fpt.producerworkbench.common.ProcessingStatus;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.TrackStatus;
import com.fpt.producerworkbench.dto.request.TrackCreateRequest;
import com.fpt.producerworkbench.dto.request.TrackUpdateRequest;
import com.fpt.producerworkbench.dto.request.TrackVersionUploadRequest;
import com.fpt.producerworkbench.dto.response.TrackResponse;
import com.fpt.producerworkbench.dto.response.TrackUploadUrlResponse;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.repository.MilestoneMoneySplitRepository;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.TrackRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.AudioProcessingService;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.TrackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackServiceImpl implements TrackService {

    private final TrackRepository trackRepository;
    private final MilestoneRepository milestoneRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MilestoneMoneySplitRepository milestoneMoneySplitRepository;
    private final FileKeyGenerator fileKeyGenerator;
    private final FileStorageService fileStorageService;
    private final AudioProcessingService audioProcessingService;

    @Override
    @Transactional
    public TrackUploadUrlResponse createTrack(Authentication auth, Long projectId, Long milestoneId, TrackCreateRequest request) {
        log.info("Tạo track mới cho milestone {}", milestoneId);

        // Kiểm tra authentication
        User currentUser = loadUser(auth);

        // Kiểm tra milestone tồn tại
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Milestone không tồn tại"));

        // Kiểm tra milestone thuộc project
        Project project = milestone.getContract().getProject();
        if (!project.getId().equals(projectId)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Milestone không thuộc project này");
        }

        // Kiểm tra quyền: phải là Owner hoặc COLLABORATOR
        checkUploadPermission(currentUser, project);

        // Validate voice tag
        if (Boolean.TRUE.equals(request.getVoiceTagEnabled())) {
            if (request.getVoiceTagText() == null || request.getVoiceTagText().isBlank()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Voice tag text không được để trống khi bật voice tag");
            }
        }

        // Tự động xác định version: nếu không có trong request, tự động tính version tiếp theo
        String version = request.getVersion();
        if (version == null || version.isBlank()) {
            version = calculateNextVersion(request.getName(), milestoneId);
            log.info("Tự động set version = {} cho track mới", version);
        }

        // Tạo track entity
        Track track = Track.builder()
                .name(request.getName())
                .description(request.getDescription())
                .version(version)
                .milestone(milestone)
                .user(currentUser)
                .voiceTagEnabled(request.getVoiceTagEnabled())
                .voiceTagText(request.getVoiceTagText())
                .contentType(request.getContentType())
                .fileSize(request.getFileSize())
                .status(TrackStatus.INTERNAL_DRAFT)
                .processingStatus(ProcessingStatus.UPLOADING)
                .build();

        track = trackRepository.save(track);
        log.info("Đã tạo track với ID: {}", track.getId());

        // Set rootTrackId = chính ID của nó (đây là track version đầu tiên)
        // parentTrackId = null (không có parent)
        track.setRootTrackId(track.getId());
        track.setParentTrackId(null);
        track = trackRepository.save(track);

        String masterKey = fileKeyGenerator.generateTrackMasterKey(
                track.getId(),
                request.getName() + getExtensionFromContentType(request.getContentType())
        );
        track.setS3OriginalKey(masterKey);
        trackRepository.save(track);

        // Tạo presigned URL để upload (15 phút) - dùng PutObject presigned URL
        String uploadUrl = fileStorageService.generateUploadPresignedUrl(
                masterKey,
                request.getContentType(),
                900L // 15 minutes
        );

        log.info("Đã tạo presigned UPLOAD URL cho track {}", track.getId());

        return TrackUploadUrlResponse.builder()
                .trackId(track.getId())
                .uploadUrl(uploadUrl)
                .s3Key(masterKey)
                .expiresIn(900L) // 15 minutes
                .build();
    }

    @Override
    @Transactional
    public void finalizeUpload(Authentication auth, Long trackId) {
        log.info("Hoàn tất upload cho track {}", trackId);

        User currentUser = loadUser(auth);
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Track không tồn tại"));

        // Kiểm tra quyền: chỉ người tạo track mới được finalize
        if (!track.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thao tác track này");
        }

        // Kiểm tra trạng thái
        if (track.getProcessingStatus() != ProcessingStatus.UPLOADING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Track không ở trạng thái UPLOADING");
        }

        // Đánh dấu đang xử lý NGAY bây giờ để tránh double finalize
        track.setProcessingStatus(ProcessingStatus.PROCESSING);
        track.setErrorMessage(null);
        trackRepository.save(track);

        // Trigger xử lý audio bất đồng bộ theo trackId
        audioProcessingService.processTrackAudio(track.getId());

        log.info("Đã trigger xử lý audio cho track {}", trackId);
    }

    @Override
    public List<TrackResponse> getTracksByMilestone(Authentication auth, Long milestoneId) {
        log.info("Lấy danh sách tracks cho milestone {}", milestoneId);

        User currentUser = loadUser(auth);

        // Kiểm tra milestone tồn tại
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Milestone không tồn tại"));

        // Kiểm tra quyền xem
        Project project = milestone.getContract().getProject();
        checkViewPermission(currentUser, project);

        List<Track> tracks = trackRepository.findByMilestoneIdOrderByCreatedAtDesc(milestoneId);

        return tracks.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TrackResponse getTrackById(Authentication auth, Long trackId) {
        log.info("Lấy thông tin track {}", trackId);

        User currentUser = loadUser(auth);
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Track không tồn tại"));

        // Kiểm tra quyền xem
        Project project = track.getMilestone().getContract().getProject();
        checkViewPermission(currentUser, project);

        return mapToResponse(track);
    }

    @Override
    @Transactional
    public TrackResponse updateTrack(Authentication auth, Long trackId, TrackUpdateRequest request) {
        log.info("Cập nhật track {}", trackId);

        User currentUser = loadUser(auth);
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Track không tồn tại"));

        // Kiểm tra quyền cập nhật: Owner hoặc COLLABORATOR (và là người tạo)
        Project project = track.getMilestone().getContract().getProject();
        checkUpdatePermission(currentUser, project, track);

        // Nếu track đang UPLOADING hoặc PROCESSING thì không cho đổi voice tag
        if (track.getProcessingStatus() == ProcessingStatus.UPLOADING
                || track.getProcessingStatus() == ProcessingStatus.PROCESSING) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Track đang được xử lý. Vui lòng đợi hoàn tất trước khi chỉnh sửa.");
        }

        boolean voiceTagChanged = false;

        // Cập nhật thông tin
        track.setName(request.getName());
        track.setDescription(request.getDescription());
        track.setVersion(request.getVersion());

        if (request.getVoiceTagEnabled() != null
                && !request.getVoiceTagEnabled().equals(track.getVoiceTagEnabled())) {
            track.setVoiceTagEnabled(request.getVoiceTagEnabled());
            voiceTagChanged = true;
        }
        if (request.getVoiceTagText() != null
                && !request.getVoiceTagText().equals(track.getVoiceTagText())) {
            track.setVoiceTagText(request.getVoiceTagText());
            voiceTagChanged = true;
        }
        if (request.getStatus() != null) {
            // Chỉ Owner được đổi status
            boolean isOwner = project.getCreator() != null
                    && currentUser.getId().equals(project.getCreator().getId());
            if (!isOwner) {
                throw new AppException(ErrorCode.ACCESS_DENIED,
                        "Chỉ Owner mới có quyền thay đổi trạng thái nội bộ của track");
            }
            track.setStatus(request.getStatus());
        }

        track = trackRepository.save(track);

        // Nếu voice tag thay đổi và đã có master file thì trigger re-process
        if (voiceTagChanged) {
            triggerReprocess(track);
        }

        log.info("Đã cập nhật track {}", trackId);

        return mapToResponse(track);
    }

    @Override
    @Transactional
    public void deleteTrack(Authentication auth, Long trackId) {
        log.info("Xóa track {}", trackId);

        User currentUser = loadUser(auth);
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Track không tồn tại"));

        // Kiểm tra quyền xóa: chỉ Owner
        Project project = track.getMilestone().getContract().getProject();
        checkDeletePermission(currentUser, project);

        // Xóa files trên S3
        try {
            if (track.getS3OriginalKey() != null) {
                fileStorageService.deleteFile(track.getS3OriginalKey());
                log.info("Đã xóa master file: {}", track.getS3OriginalKey());
            }
            if (track.getVoiceTagAudioKey() != null) {
                fileStorageService.deleteFile(track.getVoiceTagAudioKey());
                log.info("Đã xóa voice tag file: {}", track.getVoiceTagAudioKey());
            }
            // Xóa toàn bộ thư mục HLS nếu có (bao gồm cả mixed audio nếu có)
            if (track.getHlsPrefix() != null) {
                fileStorageService.deletePrefix(track.getHlsPrefix());
                log.info("Đã xóa HLS directory: {}", track.getHlsPrefix());
            }
            // Xóa thư mục mixed audio nếu có (dùng pattern)
            String mixedPrefix = "audio/mixed/" + trackId + "/";
            try {
                fileStorageService.deletePrefix(mixedPrefix);
                log.info("Đã xóa mixed audio directory: {}", mixedPrefix);
            } catch (Exception ex) {
                log.debug("Không có mixed audio directory hoặc đã bị xóa");
            }
        } catch (Exception e) {
            log.error("Lỗi khi xóa files S3 cho track {}: {}", trackId, e.getMessage());
        }

        trackRepository.delete(track);
        log.info("Đã xóa track {}", trackId);
    }

    @Override
    public String getPlaybackUrl(Authentication auth, Long trackId) {
        log.info("Lấy playback URL cho track {}", trackId);

        User currentUser = loadUser(auth);
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Track không tồn tại"));

        // Kiểm tra quyền phát: Owner hoặc COLLABORATOR
        Project project = track.getMilestone().getContract().getProject();
        checkPlayPermission(currentUser, project);

        // Kiểm tra track đã sẵn sàng chưa
        if (track.getProcessingStatus() != ProcessingStatus.READY) {
            throw new AppException(ErrorCode.BAD_REQUEST, 
                    "Track chưa sẵn sàng để phát. Trạng thái: " + track.getProcessingStatus());
        }

        if (track.getHlsPrefix() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "HLS URL không tồn tại");
        }

        // ✅ Tạo CloudFront streaming URL cho index.m3u8
        String hlsPlaylistKey = track.getHlsPrefix() + "index.m3u8";
        String playbackUrl = fileStorageService.generateStreamingUrl(hlsPlaylistKey);

        log.info("Đã tạo CloudFront playback URL cho track {}", trackId);
        return playbackUrl;
    }

    @Override
    @Transactional
    public TrackUploadUrlResponse uploadNewVersion(Authentication auth, Long trackId, TrackVersionUploadRequest request) {
        log.info("Upload version mới cho track {}", trackId);

        // Kiểm tra authentication
        User currentUser = loadUser(auth);

        // Tìm track gốc
        Track originalTrack = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Track không tồn tại"));

        // Kiểm tra quyền: phải là Owner hoặc COLLABORATOR
        Project project = originalTrack.getMilestone().getContract().getProject();
        checkUploadPermission(currentUser, project);

        // Validate voice tag
        if (Boolean.TRUE.equals(request.getVoiceTagEnabled())) {
            if (request.getVoiceTagText() == null || request.getVoiceTagText().isBlank()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Voice tag text không được để trống khi bật voice tag");
            }
        }

        // Tính version tiếp theo dựa trên track gốc
        String nextVersion = calculateNextVersion(originalTrack.getName(), originalTrack.getMilestone().getId());
        log.info("Tự động tạo version {} cho track {}", nextVersion, originalTrack.getName());

        // Xác định rootTrackId: nếu originalTrack có rootTrackId thì dùng, nếu không thì dùng chính ID của originalTrack
        // (trường hợp track cũ chưa có rootTrackId)
        Long rootTrackId = originalTrack.getRootTrackId();
        if (rootTrackId == null) {
            rootTrackId = originalTrack.getId();
        }

        // Tạo track mới với version mới
        Track newVersionTrack = Track.builder()
                .name(originalTrack.getName()) // Giữ nguyên tên
                .description(request.getDescription() != null ? request.getDescription() : originalTrack.getDescription())
                .version(nextVersion)
                .milestone(originalTrack.getMilestone())
                .user(currentUser)
                .voiceTagEnabled(request.getVoiceTagEnabled())
                .voiceTagText(request.getVoiceTagText())
                .contentType(request.getContentType())
                .fileSize(request.getFileSize())
                .status(TrackStatus.INTERNAL_DRAFT)
                .processingStatus(ProcessingStatus.UPLOADING)
                .rootTrackId(rootTrackId) // Set rootTrackId để FE có thể group các version
                .parentTrackId(originalTrack.getId()) // Set parentTrackId = id của track gốc để xây dựng cây phân cấp
                .build();

        newVersionTrack = trackRepository.save(newVersionTrack);
        log.info("Đã tạo track version mới với ID: {} và version: {}", newVersionTrack.getId(), nextVersion);

        String masterKey = fileKeyGenerator.generateTrackMasterKey(
                newVersionTrack.getId(),
                newVersionTrack.getName() + getExtensionFromContentType(request.getContentType())
        );
        newVersionTrack.setS3OriginalKey(masterKey);
        trackRepository.save(newVersionTrack);

        // Tạo presigned URL để upload (15 phút)
        String uploadUrl = fileStorageService.generateUploadPresignedUrl(
                masterKey,
                request.getContentType(),
                900L // 15 minutes
        );

        log.info("Đã tạo presigned UPLOAD URL cho track version mới {}", newVersionTrack.getId());

        return TrackUploadUrlResponse.builder()
                .trackId(newVersionTrack.getId())
                .uploadUrl(uploadUrl)
                .s3Key(masterKey)
                .expiresIn(900L) // 15 minutes
                .build();
    }

    // ========== Helper Methods ==========

    private User loadUser(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void checkUploadPermission(User user, Project project) {
        boolean isOwner = project.getCreator() != null && user.getId().equals(project.getCreator().getId());
        if (isOwner) {
            return;
        }

        // Kiểm tra COLLABORATOR
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserEmail(project.getId(), user.getEmail())
                .orElse(null);
        if (member != null && member.getProjectRole() == ProjectRole.COLLABORATOR) {
            // COLLABORATOR phải approve Money Split trước
            if (!hasApprovedMoneySplit(project, user.getId())) {
                throw new AppException(ErrorCode.ACCESS_DENIED, 
                    "Bạn cần chấp nhận phân chia tiền (Money Split) trước khi upload track");
            }
            return;
        }

        throw new AppException(ErrorCode.ACCESS_DENIED, "Chỉ Owner hoặc COLLABORATOR mới có thể upload track");
    }

    private void checkViewPermission(User user, Project project) {
        boolean isOwner = project.getCreator() != null && user.getId().equals(project.getCreator().getId());
        if (isOwner) {
            return;
        }

        // Kiểm tra COLLABORATOR
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserEmail(project.getId(), user.getEmail())
                .orElse(null);
        if (member != null && member.getProjectRole() == ProjectRole.COLLABORATOR) {
            // COLLABORATOR phải approve Money Split trước
            if (!hasApprovedMoneySplit(project, user.getId())) {
                throw new AppException(ErrorCode.ACCESS_DENIED, 
                    "Bạn cần chấp nhận phân chia tiền (Money Split) trước khi xem track");
            }
            return;
        }

        throw new AppException(ErrorCode.ACCESS_DENIED, "Chỉ Owner hoặc COLLABORATOR mới có thể xem track");
    }

    private void checkUpdatePermission(User user, Project project, Track track) {
        boolean isOwner = project.getCreator() != null && user.getId().equals(project.getCreator().getId());
        
        // Owner có thể update bất kỳ track nào
        if (isOwner) {
            return;
        }

        // COLLABORATOR chỉ có thể update track của chính mình
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserEmail(project.getId(), user.getEmail())
                .orElse(null);
        if (member != null && member.getProjectRole() == ProjectRole.COLLABORATOR) {
            // Phải approve Money Split trước
            if (!hasApprovedMoneySplit(project, user.getId())) {
                throw new AppException(ErrorCode.ACCESS_DENIED, 
                    "Bạn cần chấp nhận phân chia tiền (Money Split) trước khi cập nhật track");
            }
            
            if (track.getUser().getId().equals(user.getId())) {
                return;
            }
        }

        throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền cập nhật track này");
    }

    private void checkDeletePermission(User user, Project project) {
        boolean isOwner = project.getCreator() != null && user.getId().equals(project.getCreator().getId());
        if (!isOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Chỉ Owner mới có thể xóa track");
        }
    }

    private void checkPlayPermission(User user, Project project) {
        // Same as view permission
        checkViewPermission(user, project);
    }

    /**
     * Kiểm tra xem COLLABORATOR đã approve Money Split chưa
     */
    private boolean hasApprovedMoneySplit(Project project, Long userId) {
        if (project == null || userId == null) {
            return false;
        }
        
        List<Milestone> milestones = milestoneRepository.findByProjectIdOrderBySequenceAsc(project.getId());
        if (milestones.isEmpty()) {
            return false;
        }
        
        List<Long> milestoneIds = milestones.stream()
                .map(Milestone::getId)
                .collect(Collectors.toList());
        
        return milestoneMoneySplitRepository.existsByMilestoneIdInAndUserIdAndStatus(
                milestoneIds,
                userId,
                MoneySplitStatus.APPROVED
        );
    }

    /**
     * Trigger re-process audio khi voice tag thay đổi
     */
    private void triggerReprocess(Track track) {
        if (track.getS3OriginalKey() == null) {
            log.warn("Track {} chưa có master file nhưng voice tag đã thay đổi", track.getId());
        } else {
            track.setProcessingStatus(ProcessingStatus.PROCESSING);
            track.setErrorMessage(null);
            trackRepository.save(track);
            
            audioProcessingService.processTrackAudio(track.getId());
            log.info("Voice tag thay đổi. Đã trigger re-process audio cho track {}", track.getId());
        }
    }

    private TrackResponse mapToResponse(Track track) {
        String hlsPlaybackUrl = null;
        if (track.getProcessingStatus() == ProcessingStatus.READY && track.getHlsPrefix() != null) {
            try {
                // ✅ Dùng generateStreamingUrl để lấy CloudFront URL
                String hlsPlaylistKey = track.getHlsPrefix() + "index.m3u8";
                hlsPlaybackUrl = fileStorageService.generateStreamingUrl(hlsPlaylistKey);
            } catch (Exception e) {
                log.warn("Không thể tạo CloudFront streaming URL cho track {}: {}", track.getId(), e.getMessage());
                hlsPlaybackUrl = null;
            }
        }

        return TrackResponse.builder()
                .id(track.getId())
                .name(track.getName())
                .description(track.getDescription())
                .version(track.getVersion())
                .rootTrackId(track.getRootTrackId())
                .parentTrackId(track.getParentTrackId())
                .milestoneId(track.getMilestone().getId())
                .userId(track.getUser().getId())
                .userName(track.getUser().getFirstName() + " " + track.getUser().getLastName())
                .voiceTagEnabled(track.getVoiceTagEnabled())
                .voiceTagText(track.getVoiceTagText())
                .status(track.getStatus())
                .processingStatus(track.getProcessingStatus())
                .errorMessage(track.getErrorMessage())
                .contentType(track.getContentType())
                .fileSize(track.getFileSize())
                .duration(track.getDuration())
                .hlsPlaybackUrl(hlsPlaybackUrl)
                .createdAt(track.getCreatedAt())
                .updatedAt(track.getUpdatedAt())
                .build();
    }

    private String getExtensionFromContentType(String contentType) {
        if (contentType == null) {
            return ".wav";
        }
        return switch (contentType.toLowerCase()) {
            case "audio/wav", "audio/wave" -> ".wav";
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "audio/flac" -> ".flac";
            case "audio/aac" -> ".aac";
            case "audio/ogg" -> ".ogg";
            default -> ".wav";
        };
    }

    /**
     * Tính version tiếp theo dựa trên các tracks cùng tên trong milestone
     * Nếu không có track nào cùng tên, trả về "1"
     * Nếu có, tìm version cao nhất và tăng lên 1
     */
    private String calculateNextVersion(String trackName, Long milestoneId) {
        List<Track> existingTracks = trackRepository.findByNameAndMilestoneId(trackName, milestoneId);
        
        if (existingTracks.isEmpty()) {
            return "1";
        }

        // Tìm version số cao nhất
        int maxVersion = 0;
        for (Track track : existingTracks) {
            int versionNum = parseVersionNumber(track.getVersion());
            if (versionNum > maxVersion) {
                maxVersion = versionNum;
            }
        }

        // Trả về version tiếp theo
        return String.valueOf(maxVersion + 1);
    }

    /**
     * Parse version string thành số (hỗ trợ "1", "v1", "version 1", ...)
     * Trả về 0 nếu không parse được
     */
    private int parseVersionNumber(String version) {
        if (version == null || version.isBlank()) {
            return 0;
        }

        // Loại bỏ các ký tự không phải số
        String cleaned = version.replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) {
            return 0;
        }

        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Không thể parse version: {}", version);
            return 0;
        }
    }
}

