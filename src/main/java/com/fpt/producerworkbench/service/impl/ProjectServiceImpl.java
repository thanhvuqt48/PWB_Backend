package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.common.MilestoneStatus;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.ProjectCreateRequest;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.service.NotificationService;
import com.fpt.producerworkbench.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final NotificationService notificationService;
    private final ContractRepository contractRepository;
    private final MilestoneRepository milestoneRepository;

    private static final String NOTIFICATION_TOPIC = "notification-delivery";

    @Override
    @Transactional
    public Project createProject(ProjectCreateRequest request, String creatorEmail) {
         if (projectRepository.existsByTitle(request.getTitle())) {
             throw new AppException(ErrorCode.PROJECT_EXISTED);
         }
        User currentUser = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project newProject = Project.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .type(request.getType())
                .creator(currentUser)
                .status(ProjectStatus.PENDING)
                .build();

        Project savedProject = projectRepository.save(newProject);

        ProjectMember ownerMember = ProjectMember.builder()
                .project(savedProject)
                .user(currentUser)
                .projectRole(ProjectRole.OWNER)
                .anonymous(false)
                .build();

        projectMemberRepository.save(ownerMember);

        try {
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(currentUser.getEmail())
                    .subject("Bạn đã tạo dự án thành công: " + savedProject.getTitle())
                    .templateCode("project-creation-success-template")
                    .param(Map.of(
                            "ownerName", currentUser.getFullName(),
                            "projectName", savedProject.getTitle()
                    ))
                    .build();

            log.info("Chuẩn bị gửi thông báo tạo dự án thành công tới Kafka topic '{}'", NOTIFICATION_TOPIC);
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi thông báo tới Kafka thành công!");

        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi message thông báo tạo dự án tới Kafka!", e);
        }

        try {
            if (currentUser != null && currentUser.getId() != null) {
                String actionUrl = String.format("/projectDetail?id=%d", savedProject.getId());

                notificationService.sendNotification(
                        SendNotificationRequest.builder()
                                .userId(currentUser.getId())
                                .type(NotificationType.SYSTEM)
                                .title("Dự án đã được tạo thành công")
                                .message(String.format("Bạn đã tạo dự án \"%s\" thành công.",
                                        savedProject.getTitle()))
                                .relatedEntityType(RelatedEntityType.PROJECT)
                                .relatedEntityId(savedProject.getId())
                                .actionUrl(actionUrl)
                                .build());
            }
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho người tạo dự án: {}", e.getMessage());
        }

        return savedProject;
    }

    @Override
    @Transactional
    public Project completeProject(Long projectId, Authentication auth) {
        log.info("Xác nhận hoàn thành dự án: projectId={}", projectId);

        if (projectId == null || projectId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.getStatus() == ProjectStatus.COMPLETED) {
            throw new AppException(ErrorCode.PROJECT_ALREADY_COMPLETED);
        }

        // Chỉ CLIENT mới được xác nhận hoàn thành project
        ProjectMember projectMember = projectMemberRepository
                .findByProjectIdAndUserEmail(projectId, currentUser.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));

        if (projectMember.getProjectRole() != ProjectRole.CLIENT) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    "Chỉ khách hàng mới có quyền xác nhận hoàn thành dự án");
        }

        Contract contract = contractRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        PaymentType paymentType = contract.getPaymentType();
        if (paymentType == PaymentType.MILESTONE) {
            // Thanh toán theo cột mốc: tất cả milestones phải COMPLETED
            List<Milestone> milestones = milestoneRepository
                    .findByContractIdOrderBySequenceAsc(contract.getId());

            if (milestones.isEmpty()) {
                throw new AppException(ErrorCode.MILESTONE_NOT_FOUND);
            }

            boolean allCompleted = milestones.stream()
                    .allMatch(m -> m.getStatus() == MilestoneStatus.COMPLETED);

            if (!allCompleted) {
                throw new AppException(ErrorCode.PROJECT_MILESTONES_NOT_COMPLETED);
            }
        } else if (paymentType != PaymentType.FULL) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_TYPE);
        }

        project.setStatus(ProjectStatus.COMPLETED);
        project.setCompletedAt(LocalDateTime.now());
        Project saved = projectRepository.save(project);

        log.info("Đã xác nhận hoàn thành dự án: projectId={}, status={}", saved.getId(), saved.getStatus());

        sendProjectCompletedEmailToOwner(saved, currentUser);
        sendProjectCompletedNotificationToOwner(saved, currentUser);

        return saved;
    }

    /**
     * Gửi email cho owner khi client xác nhận hoàn thành dự án
     */
    private void sendProjectCompletedEmailToOwner(Project project, User client) {
        if (project == null || project.getCreator() == null) {
            log.warn("Không thể gửi email hoàn thành dự án: project hoặc creator null");
            return;
        }

        User owner = project.getCreator();
        if (owner.getEmail() == null || owner.getEmail().isBlank()) {
            log.warn("Không thể gửi email hoàn thành dự án: owner {} không có email", owner.getId());
            return;
        }

        try {
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(owner.getEmail())
                    .subject("Khách hàng đã xác nhận hoàn thành dự án: " + project.getTitle())
                    .templateCode("project-completed-notification")
                    .param(Map.of(
                            "ownerName", owner.getFullName() != null ? owner.getFullName() : owner.getEmail(),
                            "clientName", client.getFullName() != null ? client.getFullName() : client.getEmail(),
                            "projectName", project.getTitle()
                    ))
                    .build();

            log.info("Gửi email hoàn thành dự án tới Kafka: ownerId={}, projectId={}", owner.getId(), project.getId());
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email hoàn thành dự án qua Kafka: ownerId={}, projectId={}",
                    project.getCreator().getId(), project.getId(), e);
        }
    }

    /**
     * Gửi notification realtime cho owner khi client xác nhận hoàn thành dự án
     */
    private void sendProjectCompletedNotificationToOwner(Project project, User client) {
        if (project == null || project.getCreator() == null) {
            return;
        }

        User owner = project.getCreator();
        if (owner.getId() == null) {
            return;
        }

        try {
            String actionUrl = String.format("/projectDetail?id=%d", project.getId());

            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(owner.getId())
                            .type(NotificationType.SYSTEM)
                            .title("Dự án đã được hoàn thành")
                            .message(String.format("Khách hàng \"%s\" đã xác nhận hoàn thành dự án \"%s\".",
                                    client.getFullName() != null ? client.getFullName() : client.getEmail(),
                                    project.getTitle()))
                            .relatedEntityType(RelatedEntityType.PROJECT)
                            .relatedEntityId(project.getId())
                            .actionUrl(actionUrl)
                            .build());
        } catch (Exception e) {
            log.error("Lỗi khi gửi notification hoàn thành dự án cho ownerId={}, projectId={}: {}",
                    owner.getId(), project.getId(), e.getMessage());
        }
    }
}