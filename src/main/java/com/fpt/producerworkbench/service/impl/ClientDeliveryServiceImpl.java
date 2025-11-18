package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.*;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.SendTrackToClientRequest;
import com.fpt.producerworkbench.dto.request.UpdateClientDeliveryStatusRequest;
import com.fpt.producerworkbench.dto.response.ClientDeliveryResponse;
import com.fpt.producerworkbench.dto.response.ClientTrackResponse;
import com.fpt.producerworkbench.dto.response.TrackResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.ClientDeliveryService;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.TrackStatusTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation của ClientDeliveryService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientDeliveryServiceImpl implements ClientDeliveryService {

    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    private final MilestoneRepository milestoneRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ClientDeliveryRepository clientDeliveryRepository;
    private final MilestoneDeliveryRepository milestoneDeliveryRepository;
    private final TrackStatusTransitionService trackStatusTransitionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FileStorageService fileStorageService;

    private static final String NOTIFICATION_TOPIC = "notification-delivery";

    /**
     * Load user từ authentication
     */
    private User loadUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Kiểm tra user có phải Owner của project không
     */
    private boolean isOwner(Long userId, Project project) {
        return project.getCreator().getId().equals(userId);
    }

    /**
     * Kiểm tra user có quyền xem Client Room không
     * Permission: Owner, Admin, Client, Observer (nếu project.isFunded = true)
     */
    private boolean canAccessClientRoom(User user, Project project) {
        // Admin always has access
        if (user.getRole() == UserRole.ADMIN) {
            return true;
        }

        // Owner always has access
        if (isOwner(user.getId(), project)) {
            return true;
        }

        // Check if user is project member with CLIENT or OBSERVER role
        Optional<ProjectMember> memberOpt = projectMemberRepository.findByProjectIdAndUserId(project.getId(), user.getId());
        if (memberOpt.isPresent()) {
            ProjectRole role = memberOpt.get().getProjectRole();
            // Client và Observer chỉ được xem nếu project đã funded (có contract completed)
            if (role == ProjectRole.CLIENT || role == ProjectRole.OBSERVER) {
                // Check if project is funded (project type is COLLABORATIVE)
                return project.getType() == ProjectType.COLLABORATIVE;
            }
        }

        return false;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClientDeliveryResponse sendTrackToClient(Authentication auth, Long trackId, SendTrackToClientRequest request) {
        log.info("Starting sendTrackToClient: trackId={}", trackId);

        // 1. Load user
        User currentUser = loadUser(auth);

        // 2. Load track và validate tồn tại
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.TRACK_NOT_FOUND));

        // 3. Load milestone
        Milestone milestone = track.getMilestone();
        if (milestone == null) {
            throw new AppException(ErrorCode.MILESTONE_NOT_FOUND);
        }

        // 4. Load project
        Project project = milestone.getContract().getProject();

        // 5. Check permission: Chỉ Owner được gửi
        if (!isOwner(currentUser.getId(), project)) {
            log.warn("User {} is not owner of project {}", currentUser.getId(), project.getId());
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // 6. Validate track.status = INTERNAL_APPROVED
        if (track.getStatus() != TrackStatus.INTERNAL_APPROVED) {
            log.warn("Track {} is not approved: status={}", trackId, track.getStatus());
            throw new AppException(ErrorCode.CANNOT_SEND_UNAPPROVED_TRACK);
        }

        // 7. Validate track.processingStatus = READY
        if (track.getProcessingStatus() != ProcessingStatus.READY) {
            log.warn("Track {} is not ready: processingStatus={}", trackId, track.getProcessingStatus());
            throw new AppException(ErrorCode.TRACK_NOT_READY);
        }

        // 8. Validate track chưa được gửi
        if (clientDeliveryRepository.existsByTrackIdAndMilestoneId(trackId, milestone.getId())) {
            log.warn("Track {} already sent to client in milestone {}", trackId, milestone.getId());
            throw new AppException(ErrorCode.TRACK_ALREADY_SENT_TO_CLIENT);
        }

        // 9. Tạo ClientDelivery (không giới hạn số lần gửi)
        LocalDateTime now = LocalDateTime.now();
        ClientDelivery clientDelivery = ClientDelivery.builder()
                .track(track)
                .milestone(milestone)
                .sentBy(currentUser)
                .status(ClientDeliveryStatus.DELIVERED)
                .sentAt(now)
                .note(request.getNote())
                .build();

        clientDelivery = clientDeliveryRepository.save(clientDelivery);
        log.info("Created ClientDelivery: id={}", clientDelivery.getId());

        // 10. Log transition
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("delivery_id", clientDelivery.getId());
        metadata.put("milestone_id", milestone.getId());
        metadata.put("client_delivery_status", ClientDeliveryStatus.DELIVERED.name());

        trackStatusTransitionService.logTransition(
                track,
                TrackStatus.INTERNAL_APPROVED.name(),
                "CLIENT_DELIVERED",
                currentUser,
                "Đã gửi cho khách hàng",
                metadata
        );

        // 11. Gửi email notification cho Client và Observer
        sendClientProductReceivedNotification(track, milestone, project, currentUser);

        // 12. Return response (không cần tính quota vì không trừ khi gửi)
        return mapToClientDeliveryResponse(clientDelivery, track, currentUser, null);
    }

    @Override
    public List<ClientTrackResponse> getClientTracks(Authentication auth, Long milestoneId) {
        log.info("Getting client tracks for milestone: {}", milestoneId);

        // 1. Load user
        User currentUser = loadUser(auth);

        // 2. Load milestone
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        // 3. Load project
        Project project = milestone.getContract().getProject();

        // 4. Check permission
        if (!canAccessClientRoom(currentUser, project)) {
            log.warn("User {} cannot access client room of project {}", currentUser.getId(), project.getId());
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // 5. Query tất cả client deliveries (DELIVERED, REJECTED, REQUEST_EDIT, ACCEPTED)
        // Sắp xếp theo sentAt mới nhất
        List<ClientDelivery> deliveries = clientDeliveryRepository.findByMilestoneIdOrderBySentAtDesc(milestoneId);

        // 6. Filter tracks with INTERNAL_APPROVED status và loại bỏ CANCELLED (nếu có)
        List<ClientDelivery> validDeliveries = deliveries.stream()
                .filter(d -> d.getTrack().getStatus() == TrackStatus.INTERNAL_APPROVED)
                .collect(Collectors.toList());

        // 7. Map to response (không cần quota trong response)
        return validDeliveries.stream()
                .map(delivery -> mapToClientTrackResponse(delivery, null))
                .collect(Collectors.toList());
    }

    @Override
    public ClientTrackResponse getClientTrackByDeliveryId(Authentication auth, Long deliveryId) {
        log.info("Getting client track detail for delivery: {}", deliveryId);

        // 1. Load user
        User currentUser = loadUser(auth);

        // 2. Load ClientDelivery
        ClientDelivery delivery = clientDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.CLIENT_DELIVERY_NOT_FOUND));

        // 3. Load project
        Project project = delivery.getMilestone().getContract().getProject();

        // 4. Check permission
        if (!canAccessClientRoom(currentUser, project)) {
            log.warn("User {} cannot access client room of project {}", currentUser.getId(), project.getId());
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // 5. Validate track status
        if (delivery.getTrack().getStatus() != TrackStatus.INTERNAL_APPROVED) {
            log.warn("Track {} is not approved: status={}", delivery.getTrack().getId(), delivery.getTrack().getStatus());
            throw new AppException(ErrorCode.BAD_REQUEST, "Track không ở trạng thái INTERNAL_APPROVED");
        }

        // 6. Map to response
        return mapToClientTrackResponse(delivery, null);
    }

    @Override
    @Transactional
    public ClientDeliveryResponse updateDeliveryStatus(Authentication auth, Long deliveryId, 
                                                       UpdateClientDeliveryStatusRequest request) {
        log.info("Updating delivery status: deliveryId={}, newStatus={}", deliveryId, request.getStatus());

        // 1. Load user
        User currentUser = loadUser(auth);

        // 2. Load delivery
        ClientDelivery delivery = clientDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.CLIENT_DELIVERY_NOT_FOUND));

        // 3. Load project
        Project project = delivery.getMilestone().getContract().getProject();

        // 4. Check permission (Client, Observer, Owner)
        if (!canAccessClientRoom(currentUser, project)) {
            log.warn("User {} cannot update delivery status", currentUser.getId());
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // 5. Validate current status = DELIVERED
        if (delivery.getStatus() != ClientDeliveryStatus.DELIVERED) {
            log.warn("Delivery {} status is not DELIVERED: {}", deliveryId, delivery.getStatus());
            throw new AppException(ErrorCode.INVALID_DELIVERY_STATUS_TRANSITION);
        }

        // 6. Validate new status
        ClientDeliveryStatus newStatus = request.getStatus();
        if (newStatus != ClientDeliveryStatus.REJECTED && 
            newStatus != ClientDeliveryStatus.REQUEST_EDIT &&
            newStatus != ClientDeliveryStatus.ACCEPTED) {
            log.warn("Invalid new status: {}", newStatus);
            throw new AppException(ErrorCode.INVALID_DELIVERY_STATUS_TRANSITION);
        }

        // 7. Validate reason if REQUEST_EDIT
        if (newStatus == ClientDeliveryStatus.REQUEST_EDIT && 
            (request.getReason() == null || request.getReason().trim().isEmpty())) {
            throw new AppException(ErrorCode.REASON_REQUIRED_FOR_EDIT_REQUEST);
        }

        // 8. Validate quota còn lại (chỉ cho REJECTED và REQUEST_EDIT)
        Milestone milestone = delivery.getMilestone();
        if (newStatus == ClientDeliveryStatus.REJECTED) {
            Integer productCountRemaining = getProductCountRemaining(milestone.getId());
            if (productCountRemaining <= 0) {
                log.warn("Product count exhausted for milestone {}", milestone.getId());
                throw new AppException(ErrorCode.PRODUCT_COUNT_EXHAUSTED);
            }
        } else if (newStatus == ClientDeliveryStatus.REQUEST_EDIT) {
            Integer editCountRemaining = getEditCountRemaining(milestone.getId());
            if (editCountRemaining <= 0) {
                log.warn("Edit count exhausted for milestone {}", milestone.getId());
                throw new AppException(ErrorCode.EDIT_COUNT_EXHAUSTED);
            }
        }
        // ACCEPTED không cần validate quota, không trừ quota

        // 9. Update delivery
        delivery.setStatus(newStatus);
        delivery.setNote(request.getReason());
        clientDeliveryRepository.save(delivery);

        // 10. Tạo hoặc cập nhật MilestoneDelivery để trừ quota (chỉ cho REJECTED và REQUEST_EDIT)
        LocalDateTime now = LocalDateTime.now();
        
        if (newStatus == ClientDeliveryStatus.REJECTED) {
            // REJECTED: trừ 1 productCount
            Optional<MilestoneDelivery> existingOpt = milestoneDeliveryRepository
                    .findByClientDeliveryId(deliveryId);
            
            MilestoneDelivery milestoneDelivery;
            if (existingOpt.isPresent()) {
                // Update existing
                milestoneDelivery = existingOpt.get();
                milestoneDelivery.setProductCountUsed(1);
                milestoneDelivery.setEditCountUsed(0);
                milestoneDelivery.setStatus(DeliveryStatus.ACTIVE);
                milestoneDelivery.setDeliveredAt(now);
                milestoneDelivery.setDeliveredBy(currentUser);
                log.info("Updated MilestoneDelivery for REJECTED: productCountUsed=1");
            } else {
                // Create new
                milestoneDelivery = MilestoneDelivery.builder()
                        .milestone(delivery.getMilestone())
                        .track(delivery.getTrack())
                        .clientDelivery(delivery)
                        .deliveredBy(currentUser)
                        .productCountUsed(1)
                        .editCountUsed(0)
                        .status(DeliveryStatus.ACTIVE)
                        .deliveredAt(now)
                        .build();
                log.info("Created MilestoneDelivery for REJECTED: productCountUsed=1");
            }
            milestoneDeliveryRepository.save(milestoneDelivery);
        } else if (newStatus == ClientDeliveryStatus.REQUEST_EDIT) {
            // REQUEST_EDIT: trừ 1 editCount
            Optional<MilestoneDelivery> existingOpt = milestoneDeliveryRepository
                    .findByClientDeliveryId(deliveryId);
            
            MilestoneDelivery milestoneDelivery;
            if (existingOpt.isPresent()) {
                // Update existing
                milestoneDelivery = existingOpt.get();
                milestoneDelivery.setProductCountUsed(0);
                milestoneDelivery.setEditCountUsed(1);
                milestoneDelivery.setStatus(DeliveryStatus.ACTIVE);
                milestoneDelivery.setDeliveredAt(now);
                milestoneDelivery.setDeliveredBy(currentUser);
                log.info("Updated MilestoneDelivery for REQUEST_EDIT: editCountUsed=1");
            } else {
                // Create new
                milestoneDelivery = MilestoneDelivery.builder()
                        .milestone(delivery.getMilestone())
                        .track(delivery.getTrack())
                        .clientDelivery(delivery)
                        .deliveredBy(currentUser)
                        .productCountUsed(0)
                        .editCountUsed(1)
                        .status(DeliveryStatus.ACTIVE)
                        .deliveredAt(now)
                        .build();
                log.info("Created MilestoneDelivery for REQUEST_EDIT: editCountUsed=1");
            }
            milestoneDeliveryRepository.save(milestoneDelivery);
        }
        // ACCEPTED: không tạo MilestoneDelivery, không trừ quota

        // 11. Log transition
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("delivery_id", deliveryId);
        metadata.put("old_status", ClientDeliveryStatus.DELIVERED.name());
        metadata.put("new_status", newStatus.name());

        String transitionTo;
        if (newStatus == ClientDeliveryStatus.REJECTED) {
            transitionTo = "CLIENT_REJECTED";
        } else if (newStatus == ClientDeliveryStatus.REQUEST_EDIT) {
            transitionTo = "CLIENT_REQUEST_EDIT";
        } else {
            transitionTo = "CLIENT_ACCEPTED";
        }

        trackStatusTransitionService.logTransition(
                delivery.getTrack(),
                "CLIENT_DELIVERED",
                transitionTo,
                currentUser,
                request.getReason() != null ? request.getReason() : "Client đã chấp nhận sản phẩm",
                metadata
        );

        // 12. Gửi email cho Owner
        if (newStatus == ClientDeliveryStatus.REJECTED) {
            sendClientProductRejectedNotification(delivery, currentUser);
        } else if (newStatus == ClientDeliveryStatus.REQUEST_EDIT) {
            sendClientProductEditRequestNotification(delivery, currentUser);
        } else if (newStatus == ClientDeliveryStatus.ACCEPTED) {
            sendClientProductAcceptedNotification(delivery, currentUser);
        }

        // 13. Return response (không cần quota trong response)
        return mapToClientDeliveryResponse(delivery, delivery.getTrack(), delivery.getSentBy(), null);
    }

    @Override
    public Integer getProductCountRemaining(Long milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        int used = milestoneDeliveryRepository.sumProductCountUsedByMilestoneIdAndStatus(
                milestoneId, DeliveryStatus.ACTIVE);
        
        Integer productCount = milestone.getProductCount();
        if (productCount == null) {
            productCount = 0;
        }

        return productCount - used;
    }

    @Override
    public Integer getEditCountRemaining(Long milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        int used = milestoneDeliveryRepository.sumEditCountUsedByMilestoneIdAndStatus(
                milestoneId, DeliveryStatus.ACTIVE);
        
        Integer editCount = milestone.getEditCount();
        if (editCount == null) {
            editCount = 0;
        }

        return editCount - used;
    }

    @Override
    @Transactional
    public void cancelDelivery(Authentication auth, Long deliveryId) {
        log.info("Cancelling delivery: deliveryId={}", deliveryId);

        // 1. Load user
        User currentUser = loadUser(auth);

        // 2. Load delivery
        ClientDelivery delivery = clientDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new AppException(ErrorCode.CLIENT_DELIVERY_NOT_FOUND));

        // 3. Check permission (Only owner)
        Project project = delivery.getMilestone().getContract().getProject();
        if (!isOwner(currentUser.getId(), project)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // 4. Update MilestoneDelivery status to CANCELLED
        Optional<MilestoneDelivery> milestoneDeliveryOpt = milestoneDeliveryRepository
                .findByClientDeliveryId(deliveryId);
        
        if (milestoneDeliveryOpt.isPresent()) {
            MilestoneDelivery milestoneDelivery = milestoneDeliveryOpt.get();
            milestoneDelivery.setStatus(DeliveryStatus.CANCELLED);
            milestoneDeliveryRepository.save(milestoneDelivery);
            log.info("Cancelled MilestoneDelivery: id={}", milestoneDelivery.getId());
        }

        log.info("Delivery cancelled successfully: deliveryId={}", deliveryId);
    }

    // ==================== Helper Methods ====================

    /**
     * Map ClientDelivery to response
     */
    private ClientDeliveryResponse mapToClientDeliveryResponse(ClientDelivery delivery, Track track, 
                                                               User sentBy, Integer remaining) {
        return ClientDeliveryResponse.builder()
                .id(delivery.getId())
                .trackId(track.getId())
                .trackName(track.getName())
                .milestoneId(delivery.getMilestone().getId())
                .sentBy(sentBy.getId())
                .sentByName(sentBy.getFullName())
                .status(delivery.getStatus())
                .sentAt(java.sql.Timestamp.valueOf(delivery.getSentAt()))
                .note(delivery.getNote())
                .productCountRemaining(remaining)
                .build();
    }

    /**
     * Map ClientDelivery to ClientTrackResponse
     */
    private ClientTrackResponse mapToClientTrackResponse(ClientDelivery delivery, Integer remaining) {
        Track track = delivery.getTrack();
        User sentBy = delivery.getSentBy();

        // Build HLS playback URL using FileStorageService (giống TrackServiceImpl)
        String hlsPlaybackUrl = null;
        if (track.getProcessingStatus() == ProcessingStatus.READY && track.getHlsPrefix() != null) {
            try {
                String hlsPlaylistKey = track.getHlsPrefix() + "index.m3u8";
                hlsPlaybackUrl = fileStorageService.generateStreamingUrl(hlsPlaylistKey);
            } catch (Exception e) {
                log.warn("Không thể tạo CloudFront streaming URL cho track {}: {}", track.getId(), e.getMessage());
                hlsPlaybackUrl = null;
            }
        }

        TrackResponse trackResponse = TrackResponse.builder()
                .id(track.getId())
                .name(track.getName())
                .description(track.getDescription())
                .version(track.getVersion())
                .rootTrackId(track.getRootTrackId())
                .parentTrackId(track.getParentTrackId())
                .milestoneId(track.getMilestone().getId())
                .userId(track.getUser().getId())
                .userName(track.getUser().getFullName())
                .voiceTagEnabled(track.getVoiceTagEnabled())
                .voiceTagText(track.getVoiceTagText())
                .status(track.getStatus())
                .reason(track.getReason())
                .processingStatus(track.getProcessingStatus())
                .errorMessage(track.getErrorMessage())
                .hlsPlaybackUrl(hlsPlaybackUrl)
                .contentType(track.getContentType())
                .fileSize(track.getFileSize())
                .duration(track.getDuration())
                .createdAt(track.getCreatedAt())
                .updatedAt(track.getUpdatedAt())
                .build();

        ClientDeliveryResponse deliveryResponse = mapToClientDeliveryResponse(
                delivery, track, sentBy, remaining
        );

        return ClientTrackResponse.builder()
                .track(trackResponse)
                .delivery(deliveryResponse)
                .sentAt(java.sql.Timestamp.valueOf(delivery.getSentAt()))
                .build();
    }

    /**
     * Gửi email thông báo có sản phẩm mới trong Client Room
     */
    private void sendClientProductReceivedNotification(Track track, Milestone milestone, 
                                                       Project project, User sender) {
        try {
            // Lấy danh sách Client và Observer
            List<ProjectMember> members = projectMemberRepository.findByProjectId(project.getId());
            List<User> recipients = members.stream()
                    .filter(m -> m.getProjectRole() == ProjectRole.CLIENT || m.getProjectRole() == ProjectRole.OBSERVER)
                    .map(ProjectMember::getUser)
                    .filter(u -> u.getEmail() != null && !u.getEmail().isEmpty())
                    .collect(Collectors.toList());

            if (recipients.isEmpty()) {
                log.info("No clients or observers to notify for project {}", project.getId());
                return;
            }

            String projectUrl = String.format("http://localhost:5173/projects/%d/milestones/%d/client-room", 
                    project.getId(), milestone.getId());

            for (User recipient : recipients) {
                Map<String, Object> params = new HashMap<>();
                params.put("recipientName", recipient.getFullName() != null ? recipient.getFullName() : recipient.getEmail());
                params.put("trackName", track.getName());
                params.put("milestoneTitle", milestone.getTitle());
                params.put("projectName", project.getTitle());
                params.put("senderName", sender.getFullName());
                params.put("projectUrl", projectUrl);

                NotificationEvent event = NotificationEvent.builder()
                        .recipient(recipient.getEmail())
                        .subject("Có sản phẩm mới trong Client Room")
                        .templateCode("client-product-received-notification")
                        .param(params)
                        .build();

                kafkaTemplate.send(NOTIFICATION_TOPIC, event);
                log.info("Sent client product received notification to {}", recipient.getEmail());
            }
        } catch (Exception e) {
            log.error("Error sending client product received notification", e);
        }
    }

    /**
     * Gửi email thông báo khách hàng từ chối sản phẩm
     */
    private void sendClientProductRejectedNotification(ClientDelivery delivery, User rejectedBy) {
        try {
            Track track = delivery.getTrack();
            Project project = delivery.getMilestone().getContract().getProject();
            User owner = project.getCreator();

            if (owner.getEmail() == null || owner.getEmail().isEmpty()) {
                log.warn("Owner {} has no email", owner.getId());
                return;
            }

            String trackUrl = String.format("http://localhost:5173/projects/%d/tracks/%d", 
                    project.getId(), track.getId());

            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", owner.getFullName() != null ? owner.getFullName() : owner.getEmail());
            params.put("trackName", track.getName());
            params.put("reason", delivery.getNote() != null ? delivery.getNote() : "Không có lý do cụ thể");
            params.put("rejectedBy", rejectedBy.getFullName());
            params.put("trackUrl", trackUrl);

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(owner.getEmail())
                    .subject("Khách hàng đã từ chối sản phẩm")
                    .templateCode("client-product-rejected-notification")
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Sent client product rejected notification to owner {}", owner.getEmail());
        } catch (Exception e) {
            log.error("Error sending client product rejected notification", e);
        }
    }

    /**
     * Gửi email thông báo khách hàng yêu cầu chỉnh sửa sản phẩm
     */
    private void sendClientProductEditRequestNotification(ClientDelivery delivery, User requestedBy) {
        try {
            Track track = delivery.getTrack();
            Project project = delivery.getMilestone().getContract().getProject();
            User owner = project.getCreator();

            if (owner.getEmail() == null || owner.getEmail().isEmpty()) {
                log.warn("Owner {} has no email", owner.getId());
                return;
            }

            String trackUrl = String.format("http://localhost:5173/projects/%d/tracks/%d", 
                    project.getId(), track.getId());

            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", owner.getFullName() != null ? owner.getFullName() : owner.getEmail());
            params.put("trackName", track.getName());
            params.put("reason", delivery.getNote() != null ? delivery.getNote() : "Không có yêu cầu cụ thể");
            params.put("requestedBy", requestedBy.getFullName());
            params.put("trackUrl", trackUrl);

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(owner.getEmail())
                    .subject("Khách hàng yêu cầu chỉnh sửa sản phẩm")
                    .templateCode("client-product-edit-request-notification")
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Sent client product edit request notification to owner {}", owner.getEmail());
        } catch (Exception e) {
            log.error("Error sending client product edit request notification", e);
        }
    }

    /**
     * Gửi email thông báo khách hàng đã chấp nhận sản phẩm
     */
    private void sendClientProductAcceptedNotification(ClientDelivery delivery, User acceptedBy) {
        try {
            Track track = delivery.getTrack();
            Project project = delivery.getMilestone().getContract().getProject();
            User owner = project.getCreator();

            if (owner.getEmail() == null || owner.getEmail().isEmpty()) {
                log.warn("Owner {} has no email", owner.getId());
                return;
            }

            String trackUrl = String.format("http://localhost:5173/projects/%d/tracks/%d", 
                    project.getId(), track.getId());

            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", owner.getFullName() != null ? owner.getFullName() : owner.getEmail());
            params.put("trackName", track.getName());
            params.put("reason", delivery.getNote());
            params.put("acceptedBy", acceptedBy.getFullName());
            params.put("trackUrl", trackUrl);

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(owner.getEmail())
                    .subject("Khách hàng đã chấp nhận sản phẩm")
                    .templateCode("client-product-accepted-notification")
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Sent client product accepted notification to owner {}", owner.getEmail());
        } catch (Exception e) {
            log.error("Error sending client product accepted notification", e);
        }
    }
}

