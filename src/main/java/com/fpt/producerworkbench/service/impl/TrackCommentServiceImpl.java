package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.CommentStatus;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.TrackCommentCreateRequest;
import com.fpt.producerworkbench.dto.request.TrackCommentStatusUpdateRequest;
import com.fpt.producerworkbench.dto.request.TrackCommentUpdateRequest;
import com.fpt.producerworkbench.dto.response.TrackCommentResponse;
import com.fpt.producerworkbench.dto.response.TrackCommentStatisticsResponse;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.entity.ClientDelivery;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.entity.TrackComment;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ClientDeliveryRepository;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.TrackCommentRepository;
import com.fpt.producerworkbench.repository.TrackRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.TrackCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation của TrackCommentService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackCommentServiceImpl implements TrackCommentService {

    private final TrackCommentRepository trackCommentRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final ClientDeliveryRepository clientDeliveryRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    private static final String NOTIFICATION_TOPIC = "notification-delivery";

    @Override
    @Transactional
    public TrackCommentResponse createComment(Authentication auth, Long trackId, TrackCommentCreateRequest request) {
        log.info("Tạo comment mới cho track {}", trackId);

        // Lấy user hiện tại
        User currentUser = loadUser(auth);

        // Kiểm tra track tồn tại
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.TRACK_NOT_FOUND));

        // Validate timestamp nếu có
        if (request.getTimestamp() != null) {
            if (request.getTimestamp() < 0) {
                throw new AppException(ErrorCode.INVALID_TIMESTAMP);
            }
            // Kiểm tra timestamp không vượt quá duration (nếu track có duration)
            if (track.getDuration() != null && request.getTimestamp() > track.getDuration()) {
                throw new AppException(ErrorCode.INVALID_TIMESTAMP);
            }
        }

        // Kiểm tra parent comment nếu là reply
        TrackComment parentComment = null;
        if (request.getParentCommentId() != null) {
            parentComment = trackCommentRepository.findByIdAndNotDeleted(request.getParentCommentId())
                    .orElseThrow(() -> new AppException(ErrorCode.PARENT_COMMENT_NOT_FOUND));

            // Kiểm tra parent comment thuộc về track này
            if (!parentComment.getTrack().getId().equals(trackId)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Comment cha không thuộc về track này");
            }

            // Không cho phép reply comment đã bị xóa
            if (Boolean.TRUE.equals(parentComment.getIsDeleted())) {
                throw new AppException(ErrorCode.CANNOT_REPLY_TO_DELETED_COMMENT);
            }
        }

        // Tạo comment
        TrackComment comment = TrackComment.builder()
                .track(track)
                .user(currentUser)
                .content(request.getContent())
                .timestamp(request.getTimestamp())
                .status(CommentStatus.PENDING)
                .parentComment(parentComment)
                .isDeleted(false)
                .build();

        TrackComment savedComment = trackCommentRepository.save(comment);
        log.info("Đã tạo comment {} cho track {}", savedComment.getId(), trackId);

        // Gửi email thông báo cho track owner qua Kafka
        sendNewCommentNotification(savedComment, track);

        return mapToResponse(savedComment, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TrackCommentResponse> getRootCommentsByTrack(Authentication auth, Long trackId, Pageable pageable) {
        log.info("Lấy danh sách comment gốc cho track {}", trackId);

        // Lấy user hiện tại
        loadUser(auth);

        // Kiểm tra track tồn tại
        if (!trackRepository.existsById(trackId)) {
            throw new AppException(ErrorCode.TRACK_NOT_FOUND);
        }

        Page<TrackComment> comments = trackCommentRepository.findRootCommentsByTrackId(trackId, pageable);

        return comments.map(comment -> mapToResponse(comment, false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackCommentResponse> getRepliesByComment(Authentication auth, Long commentId) {
        log.info("Lấy danh sách reply cho comment {}", commentId);

        // Lấy user hiện tại
        loadUser(auth);

        // Kiểm tra comment tồn tại
        if (!trackCommentRepository.findByIdAndNotDeleted(commentId).isPresent()) {
            throw new AppException(ErrorCode.TRACK_COMMENT_NOT_FOUND);
        }

        List<TrackComment> replies = trackCommentRepository.findRepliesByParentCommentId(commentId);

        // Load replies nested nhiều cấp (giống Facebook)
        return replies.stream()
                .map(reply -> mapToResponse(reply, true))  // ← Đổi từ false thành true để load nested
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TrackCommentResponse getCommentById(Authentication auth, Long commentId) {
        log.info("Lấy thông tin comment {}", commentId);

        // Lấy user hiện tại
        loadUser(auth);

        TrackComment comment = trackCommentRepository.findByIdAndNotDeleted(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.TRACK_COMMENT_NOT_FOUND));

        return mapToResponse(comment, true); // Load with replies
    }

    @Override
    @Transactional
    public TrackCommentResponse updateComment(Authentication auth, Long commentId, TrackCommentUpdateRequest request) {
        log.info("Cập nhật comment {}", commentId);

        // Lấy user hiện tại
        User currentUser = loadUser(auth);

        // Lấy comment
        TrackComment comment = trackCommentRepository.findByIdAndNotDeleted(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.TRACK_COMMENT_NOT_FOUND));

        // Kiểm tra quyền: chỉ user tạo comment mới được sửa
        if (!comment.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.NOT_COMMENT_OWNER);
        }

        // Cập nhật nội dung
        comment.setContent(request.getContent());
        TrackComment updatedComment = trackCommentRepository.save(comment);

        log.info("Đã cập nhật comment {}", commentId);

        return mapToResponse(updatedComment, false);
    }

    @Override
    @Transactional
    public void deleteComment(Authentication auth, Long commentId) {
        log.info("Xóa comment {}", commentId);

        // Lấy user hiện tại
        User currentUser = loadUser(auth);

        // Lấy comment
        TrackComment comment = trackCommentRepository.findByIdAndNotDeleted(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.TRACK_COMMENT_NOT_FOUND));

        // Kiểm tra quyền: user tạo comment hoặc track owner
        boolean isCommentOwner = comment.getUser().getId().equals(currentUser.getId());
        boolean isTrackOwner = comment.getTrack().getUser().getId().equals(currentUser.getId());

        if (!isCommentOwner && !isTrackOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED, 
                "Chỉ người tạo comment hoặc chủ track mới có quyền xóa");
        }

        // Soft delete
        comment.setIsDeleted(true);
        trackCommentRepository.save(comment);

        log.info("Đã xóa comment {}", commentId);
    }

    @Override
    @Transactional
    public TrackCommentResponse updateCommentStatus(Authentication auth, Long commentId, 
                                                   TrackCommentStatusUpdateRequest request) {
        log.info("Cập nhật trạng thái comment {} thành {}", commentId, request.getStatus());

        // Lấy user hiện tại
        User currentUser = loadUser(auth);

        // Lấy comment
        TrackComment comment = trackCommentRepository.findByIdAndNotDeleted(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.TRACK_COMMENT_NOT_FOUND));

        // Kiểm tra quyền: chỉ track owner mới được đổi status
        if (!comment.getTrack().getUser().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.NOT_TRACK_OWNER);
        }

        // Lưu status cũ để log
        CommentStatus oldStatus = comment.getStatus();

        // Cập nhật status
        comment.setStatus(request.getStatus());
        TrackComment updatedComment = trackCommentRepository.save(comment);

        log.info("Đã cập nhật status comment {} từ {} thành {}", 
                commentId, oldStatus, request.getStatus());

        // Gửi email thông báo cho comment owner qua Kafka
        sendStatusUpdateNotification(updatedComment, oldStatus);

        return mapToResponse(updatedComment, false);
    }

    @Override
    @Transactional(readOnly = true)
    public TrackCommentStatisticsResponse getCommentStatistics(Authentication auth, Long trackId) {
        log.info("Lấy thống kê comment trong Internal Room cho track {}", trackId);

        // Lấy user hiện tại
        loadUser(auth);

        // Kiểm tra track tồn tại
        if (!trackRepository.existsById(trackId)) {
            throw new AppException(ErrorCode.TRACK_NOT_FOUND);
        }

        // Lấy số lượng theo từng status - CHỈ ĐẾM INTERNAL ROOM COMMENTS (clientDelivery IS NULL)
        Long totalComments = trackCommentRepository.countByTrackIdInternal(trackId);
        Long pendingComments = trackCommentRepository.countByTrackIdAndStatusInternal(trackId, CommentStatus.PENDING);
        Long inProgressComments = trackCommentRepository.countByTrackIdAndStatusInternal(trackId, CommentStatus.IN_PROGRESS);
        Long resolvedComments = trackCommentRepository.countByTrackIdAndStatusInternal(trackId, CommentStatus.RESOLVED);

        return TrackCommentStatisticsResponse.builder()
                .trackId(trackId)
                .totalComments(totalComments)
                .pendingComments(pendingComments)
                .inProgressComments(inProgressComments)
                .resolvedComments(resolvedComments)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackCommentResponse> getCommentsByTimestamp(Authentication auth, Long trackId, Integer timestamp) {
        log.info("Lấy comment tại timestamp {} của track {}", timestamp, trackId);

        // Lấy user hiện tại
        loadUser(auth);

        // Kiểm tra track tồn tại
        if (!trackRepository.existsById(trackId)) {
            throw new AppException(ErrorCode.TRACK_NOT_FOUND);
        }

        List<TrackComment> comments = trackCommentRepository.findByTrackIdAndTimestamp(trackId, timestamp);

        return comments.stream()
                .map(comment -> mapToResponse(comment, false))
                .collect(Collectors.toList());
    }

    // ==================== Client Room Comments ====================

    @Override
    @Transactional
    public TrackCommentResponse createClientRoomComment(Authentication auth, Long deliveryId, TrackCommentCreateRequest request) {
        log.info("Tạo comment mới trong Client Room cho delivery {}", deliveryId);

        // 1. Load user
        User currentUser = loadUser(auth);

        // 2. Load ClientDelivery
        ClientDelivery delivery = clientDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.CLIENT_DELIVERY_NOT_FOUND));

        // 3. Load track và project
        Track track = delivery.getTrack();
        Project project = delivery.getMilestone().getContract().getProject();

        // 4. Check permission: user phải có quyền truy cập Client Room
        if (!canAccessClientRoom(currentUser, project)) {
            log.warn("User {} cannot access client room of project {}", currentUser.getId(), project.getId());
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // 5. Validate timestamp nếu có
        if (request.getTimestamp() != null) {
            if (request.getTimestamp() < 0) {
                throw new AppException(ErrorCode.INVALID_TIMESTAMP);
            }
            if (track.getDuration() != null && request.getTimestamp() > track.getDuration()) {
                throw new AppException(ErrorCode.INVALID_TIMESTAMP);
            }
        }

        // 6. Kiểm tra parent comment nếu là reply
        TrackComment parentComment = null;
        if (request.getParentCommentId() != null) {
            parentComment = trackCommentRepository.findByIdAndNotDeleted(request.getParentCommentId())
                    .orElseThrow(() -> new AppException(ErrorCode.PARENT_COMMENT_NOT_FOUND));

            // Kiểm tra parent comment thuộc về cùng ClientDelivery
            if (parentComment.getClientDelivery() == null || 
                !parentComment.getClientDelivery().getId().equals(deliveryId)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Comment cha không thuộc về Client Room này");
            }

            if (Boolean.TRUE.equals(parentComment.getIsDeleted())) {
                throw new AppException(ErrorCode.CANNOT_REPLY_TO_DELETED_COMMENT);
            }
        }

        // 7. Tạo comment với clientDelivery
        TrackComment comment = TrackComment.builder()
                .track(track)
                .clientDelivery(delivery)
                .user(currentUser)
                .content(request.getContent())
                .timestamp(request.getTimestamp())
                .status(CommentStatus.PENDING)
                .parentComment(parentComment)
                .isDeleted(false)
                .build();

        TrackComment savedComment = trackCommentRepository.save(comment);
        log.info("Đã tạo comment {} trong Client Room cho delivery {}", savedComment.getId(), deliveryId);

        // 8. Gửi email thông báo
        sendClientRoomCommentNotification(savedComment, delivery, currentUser, project);

        return mapToResponse(savedComment, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TrackCommentResponse> getRootCommentsByClientDelivery(Authentication auth, Long deliveryId, Pageable pageable) {
        log.info("Lấy danh sách comment gốc trong Client Room cho delivery {}", deliveryId);

        // 1. Load user
        User currentUser = loadUser(auth);

        // 2. Load ClientDelivery
        ClientDelivery delivery = clientDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.CLIENT_DELIVERY_NOT_FOUND));

        // 3. Load project và check permission
        Project project = delivery.getMilestone().getContract().getProject();
        if (!canAccessClientRoom(currentUser, project)) {
            log.warn("User {} cannot access client room of project {}", currentUser.getId(), project.getId());
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // 4. Query comments
        Page<TrackComment> comments = trackCommentRepository.findRootCommentsByClientDeliveryId(deliveryId, pageable);

        return comments.map(comment -> mapToResponse(comment, false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackCommentResponse> getClientRoomRepliesByComment(Authentication auth, Long commentId) {
        log.info("Lấy danh sách reply trong Client Room cho comment {}", commentId);

        // 1. Load user
        loadUser(auth);

        // 2. Kiểm tra comment tồn tại và thuộc Client Room
        TrackComment parentComment = trackCommentRepository.findByIdAndNotDeleted(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.TRACK_COMMENT_NOT_FOUND));

        if (parentComment.getClientDelivery() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Comment này không thuộc Client Room");
        }

        // 3. Query replies
        List<TrackComment> replies = trackCommentRepository.findClientRoomRepliesByParentCommentId(commentId);

        return replies.stream()
                .map(reply -> mapToResponse(reply, true))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackCommentResponse> getClientRoomCommentsByTimestamp(Authentication auth, Long deliveryId, Integer timestamp) {
        log.info("Lấy comment tại timestamp {} trong Client Room cho delivery {}", timestamp, deliveryId);

        // 1. Load user
        User currentUser = loadUser(auth);

        // 2. Load ClientDelivery
        ClientDelivery delivery = clientDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.CLIENT_DELIVERY_NOT_FOUND));

        // 3. Load project và check permission
        Project project = delivery.getMilestone().getContract().getProject();
        if (!canAccessClientRoom(currentUser, project)) {
            log.warn("User {} cannot access client room of project {}", currentUser.getId(), project.getId());
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // 4. Validate timestamp
        if (timestamp == null || timestamp < 0) {
            throw new AppException(ErrorCode.INVALID_TIMESTAMP);
        }

        // 5. Query comments
        List<TrackComment> comments = trackCommentRepository.findByClientDeliveryIdAndTimestamp(deliveryId, timestamp);

        return comments.stream()
                .map(comment -> mapToResponse(comment, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TrackCommentStatisticsResponse getClientRoomCommentStatistics(Authentication auth, Long deliveryId) {
        log.info("Lấy thống kê comment trong Client Room cho delivery {}", deliveryId);

        // 1. Load user
        User currentUser = loadUser(auth);

        // 2. Load ClientDelivery
        ClientDelivery delivery = clientDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.CLIENT_DELIVERY_NOT_FOUND));

        // 3. Load project và check permission
        Project project = delivery.getMilestone().getContract().getProject();
        if (!canAccessClientRoom(currentUser, project)) {
            log.warn("User {} cannot access client room of project {}", currentUser.getId(), project.getId());
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // 4. Lấy số lượng theo từng status
        Long totalComments = trackCommentRepository.countByClientDeliveryId(deliveryId);
        Long pendingComments = trackCommentRepository.countByClientDeliveryIdAndStatus(deliveryId, CommentStatus.PENDING);
        Long inProgressComments = trackCommentRepository.countByClientDeliveryIdAndStatus(deliveryId, CommentStatus.IN_PROGRESS);
        Long resolvedComments = trackCommentRepository.countByClientDeliveryIdAndStatus(deliveryId, CommentStatus.RESOLVED);

        return TrackCommentStatisticsResponse.builder()
                .trackId(delivery.getTrack().getId())
                .totalComments(totalComments)
                .pendingComments(pendingComments)
                .inProgressComments(inProgressComments)
                .resolvedComments(resolvedComments)
                .build();
    }

    /**
     * Helper method: Kiểm tra user có quyền truy cập Client Room
     */
    private boolean canAccessClientRoom(User user, Project project) {
        // Admin always has access
        if (user.getRole() == UserRole.ADMIN) {
            return true;
        }

        // Owner always has access
        if (project.getCreator() != null && user.getId().equals(project.getCreator().getId())) {
            return true;
        }

        // Check if user is project member with CLIENT or OBSERVER role
        java.util.Optional<ProjectMember> memberOpt = projectMemberRepository.findByProjectIdAndUserId(project.getId(), user.getId());
        if (memberOpt.isPresent()) {
            ProjectRole role = memberOpt.get().getProjectRole();
            // Client và Observer chỉ được xem nếu project đã funded
            if (role == ProjectRole.CLIENT || role == ProjectRole.OBSERVER) {
                return project.getType() == com.fpt.producerworkbench.common.ProjectType.COLLABORATIVE;
            }
        }

        return false;
    }

    /**
     * Helper method: Gửi email thông báo comment trong Client Room
     * Gửi email cho project creator (chủ dự án) thay vì track owner
     */
    private void sendClientRoomCommentNotification(TrackComment comment, ClientDelivery delivery, User commenter, Project project) {
        try {
            Track track = delivery.getTrack();
            User projectCreator = project.getCreator(); // Chủ dự án (project creator)
            boolean isCommenterProjectCreator = projectCreator.getId().equals(commenter.getId());

            // Lấy danh sách Client và Observer để gửi email
            List<ProjectMember> members = projectMemberRepository.findByProjectId(project.getId());
            List<User> clientsAndObservers = members.stream()
                    .filter(m -> m.getProjectRole() == ProjectRole.CLIENT || m.getProjectRole() == ProjectRole.OBSERVER)
                    .map(ProjectMember::getUser)
                    .filter(u -> u.getEmail() != null && !u.getEmail().isEmpty())
                    .collect(Collectors.toList());

            if (isCommenterProjectCreator) {
                // Project Creator comment -> gửi email cho Client/Observer
                for (User recipient : clientsAndObservers) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("recipientName", recipient.getFullName() != null ? recipient.getFullName() : recipient.getEmail());
                    params.put("commenterName", commenter.getFullName());
                    params.put("commenterAvatar", commenter.getAvatarUrl() != null ? 
                              commenter.getAvatarUrl() : "https://via.placeholder.com/48");
                    params.put("trackName", track.getName());
                    params.put("commentContent", comment.getContent());
                    params.put("timestamp", comment.getTimestamp() != null ? 
                              formatTimestamp(comment.getTimestamp()) : "Không có timestamp");
                    params.put("trackLink", String.format("http://localhost:5173/projects/%d/milestones/%d/client-room", 
                            project.getId(), delivery.getMilestone().getId()));

                    NotificationEvent event = NotificationEvent.builder()
                            .channel("EMAIL")
                            .recipient(recipient.getEmail())
                            .templateCode("track-new-comment-notification")
                            .subject("💬 Chủ dự án đã comment trên sản phẩm: " + track.getName())
                            .param(params)
                            .build();

                    kafkaTemplate.send(NOTIFICATION_TOPIC, event);
                    log.info("Đã gửi email thông báo comment cho client/observer {}", recipient.getEmail());
                }
            } else {
                // Client/Observer comment -> gửi email cho Project Creator (chủ dự án)
                if (projectCreator.getEmail() != null && !projectCreator.getEmail().isEmpty()) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("trackOwnerName", projectCreator.getFullName());
                    params.put("commenterName", commenter.getFullName());
                    params.put("commenterAvatar", commenter.getAvatarUrl() != null ? 
                              commenter.getAvatarUrl() : "https://via.placeholder.com/48");
                    params.put("trackName", track.getName());
                    params.put("commentContent", comment.getContent());
                    params.put("timestamp", comment.getTimestamp() != null ? 
                              formatTimestamp(comment.getTimestamp()) : "Không có timestamp");
                    params.put("trackLink", String.format("http://localhost:5173/projects/%d/milestones/%d/client-room", 
                            project.getId(), delivery.getMilestone().getId()));

                    NotificationEvent event = NotificationEvent.builder()
                            .channel("EMAIL")
                            .recipient(projectCreator.getEmail())
                            .templateCode("track-new-comment-notification")
                            .subject("💬 Bạn có comment mới trong Client Room: " + track.getName())
                            .param(params)
                            .build();

                    kafkaTemplate.send(NOTIFICATION_TOPIC, event);
                    log.info("Đã gửi email thông báo comment cho project creator {}", projectCreator.getEmail());
                }
            }

        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo comment trong Client Room: {}", e.getMessage());
        }
    }

    /**
     * Helper method: Load user từ authentication
     */
    private User loadUser(Authentication auth) {
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Helper method: Map entity sang response DTO
     * Hỗ trợ load replies nested nhiều cấp (giống Facebook)
     */
    private TrackCommentResponse mapToResponse(TrackComment comment, boolean loadReplies) {
        User user = comment.getUser();

        TrackCommentResponse.UserBasicInfo userInfo = TrackCommentResponse.UserBasicInfo.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .build();

        // Đếm số reply (phân biệt Internal Room và Client Room)
        Long replyCount;
        if (comment.getClientDelivery() != null) {
            // Client Room comment
            replyCount = trackCommentRepository.countClientRoomRepliesByParentCommentId(comment.getId());
        } else {
            // Internal Room comment
            replyCount = trackCommentRepository.countRepliesByParentCommentId(comment.getId());
        }

        TrackCommentResponse response = TrackCommentResponse.builder()
                .id(comment.getId())
                .trackId(comment.getTrack().getId())
                .user(userInfo)
                .content(comment.getContent())
                .timestamp(comment.getTimestamp())
                .status(comment.getStatus())
                .parentCommentId(comment.getParentComment() != null ? 
                                comment.getParentComment().getId() : null)
                .replyCount(replyCount)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();

        // Load replies NESTED nhiều cấp nếu được yêu cầu (giống Facebook)
        // Mỗi reply cũng sẽ load replies của nó (recursive)
        if (loadReplies && replyCount > 0) {
            List<TrackComment> replies;
            if (comment.getClientDelivery() != null) {
                // Client Room comment
                replies = trackCommentRepository.findClientRoomRepliesByParentCommentId(comment.getId());
            } else {
                // Internal Room comment
                replies = trackCommentRepository.findRepliesByParentCommentId(comment.getId());
            }
            response.setReplies(replies.stream()
                    .map(reply -> mapToResponse(reply, true))  // ← Đổi từ false thành true để load nested
                    .collect(Collectors.toList()));
        }

        return response;
    }

    /**
     * Helper method: Gửi email thông báo comment mới cho track owner
     */
    private void sendNewCommentNotification(TrackComment comment, Track track) {
        try {
            User trackOwner = track.getUser();
            User commenter = comment.getUser();

            // Không gửi email nếu người comment chính là track owner
            if (trackOwner.getId().equals(commenter.getId())) {
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("trackOwnerName", trackOwner.getFullName());
            params.put("commenterName", commenter.getFullName());
            params.put("commenterAvatar", commenter.getAvatarUrl() != null ? 
                      commenter.getAvatarUrl() : "https://via.placeholder.com/48");
            params.put("trackName", track.getName());
            params.put("trackVersion", track.getVersion());
            params.put("commentContent", comment.getContent());
            params.put("timestamp", comment.getTimestamp() != null ? 
                      formatTimestamp(comment.getTimestamp()) : "Không có timestamp");
            params.put("trackLink", "https://producerworkbench.com/tracks/" + track.getId());

            NotificationEvent event = NotificationEvent.builder()
                    .channel("EMAIL")
                    .recipient(trackOwner.getEmail())
                    .templateCode("track-new-comment-notification")
                    .subject("💬 Bạn có comment mới trên track: " + track.getName())
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo comment mới cho track owner {}", trackOwner.getEmail());

        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo comment mới: {}", e.getMessage());
            // Không throw exception để không ảnh hưởng đến flow chính
        }
    }

    /**
     * Helper method: Gửi email thông báo khi status comment thay đổi
     */
    private void sendStatusUpdateNotification(TrackComment comment, CommentStatus oldStatus) {
        try {
            User commentOwner = comment.getUser();
            User trackOwner = comment.getTrack().getUser();

            // Không gửi email nếu người comment chính là track owner
            if (commentOwner.getId().equals(trackOwner.getId())) {
                return;
            }

            String statusText = getStatusText(comment.getStatus());
            String oldStatusText = getStatusText(oldStatus);

            Map<String, Object> params = new HashMap<>();
            params.put("commentOwnerName", commentOwner.getFullName());
            params.put("trackOwnerName", trackOwner.getFullName());
            params.put("trackOwnerAvatar", trackOwner.getAvatarUrl() != null ? 
                      trackOwner.getAvatarUrl() : "https://via.placeholder.com/48");
            params.put("trackName", comment.getTrack().getName());
            params.put("trackVersion", comment.getTrack().getVersion());
            params.put("commentContent", comment.getContent());
            params.put("oldStatus", oldStatusText);
            params.put("newStatus", statusText);
            params.put("statusColor", getStatusColor(comment.getStatus()));
            params.put("trackLink", "https://producerworkbench.com/tracks/" + comment.getTrack().getId());

            NotificationEvent event = NotificationEvent.builder()
                    .channel("EMAIL")
                    .recipient(commentOwner.getEmail())
                    .templateCode("track-comment-status-update-notification")
                    .subject("🔔 Trạng thái comment của bạn đã được cập nhật")
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo status update cho comment owner {}", commentOwner.getEmail());

        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo status update: {}", e.getMessage());
            // Không throw exception để không ảnh hưởng đến flow chính
        }
    }

    /**
     * Helper method: Format timestamp thành dạng MM:SS
     */
    private String formatTimestamp(Integer seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    /**
     * Helper method: Get text hiển thị của status
     */
    private String getStatusText(CommentStatus status) {
        switch (status) {
            case PENDING:
                return "Chưa xử lý";
            case IN_PROGRESS:
                return "Đang xử lý";
            case RESOLVED:
                return "Đã xử lý";
            default:
                return status.name();
        }
    }

    /**
     * Helper method: Get màu cho status badge
     */
    private String getStatusColor(CommentStatus status) {
        switch (status) {
            case PENDING:
                return "#FFA500"; // Orange
            case IN_PROGRESS:
                return "#4169E1"; // Royal Blue
            case RESOLVED:
                return "#32CD32"; // Lime Green
            default:
                return "#808080"; // Gray
        }
    }
}



