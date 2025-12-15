package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.dto.request.ProjectReviewRequest;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.dto.response.ProjectReviewResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectReview;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.ProjectReviewRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.NotificationService;
import com.fpt.producerworkbench.service.EmailService;
import com.fpt.producerworkbench.service.ProjectReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectReviewServiceImpl implements ProjectReviewService {

    private final ProjectRepository projectRepository;
    private final ProjectReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Override
    @Transactional
    public ProjectReviewResponse createReview(Long projectId, ProjectReviewRequest request, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (project.getClient() == null || !currentUser.getId().equals(project.getClient().getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền đánh giá dự án này");
        }

        if (project.getStatus() != ProjectStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVALID_ACTION, "Dự án phải hoàn thành mới được phép đánh giá");
        }

        if (reviewRepository.existsByProjectId(projectId)) {
            throw new AppException(ErrorCode.RESOURCE_EXISTED, "Bạn đã gửi đánh giá cho dự án này rồi");
        }

        boolean isAllowedPublic = Boolean.TRUE.equals(request.getAllowPublicPortfolio());

        ProjectReview review = ProjectReview.builder()
                .project(project)
                .reviewer(currentUser)
                .targetUser(project.getCreator())
                .rating(request.getRating())
                .comment(request.getComment())
                .allowPublicPortfolio(isAllowedPublic)
                .build();

        ProjectReview savedReview = reviewRepository.save(review);
        log.info("Client {} reviewed Project {}. Public Allowed: {}", currentUser.getEmail(), projectId, isAllowedPublic);

        // Gửi notification cho Producer (Owner) và Client (async, sau khi transaction commit)
        Long producerId = project.getCreator().getId();
        Long clientId = currentUser.getId();
        String clientName = currentUser.getFullName();
        String producerName = project.getCreator().getFullName();
        String producerEmail = project.getCreator().getEmail();
        String projectTitle = project.getTitle();
        Long projectIdForNotif = project.getId();
        Integer rating = request.getRating();
        String comment = request.getComment();
        
        log.info("Preparing to send notifications - ProducerId: {}, ClientId: {}, ProjectId: {}", 
                producerId, clientId, projectIdForNotif);
        
        // Chạy async để không block transaction
        new Thread(() -> {
            try {
                Thread.sleep(500); // Đợi transaction commit
                log.info("Sending notifications after transaction commit...");
                // Gửi cho Producer (Owner)
                sendReviewNotificationToProducer(producerId, producerEmail, producerName, clientName, 
                        projectTitle, projectIdForNotif, rating, comment);
                // Gửi cho Client (confirmation)
                sendReviewConfirmationToClient(clientId, producerName, projectTitle, rating);
            } catch (Exception e) {
                log.error("Failed to send review notifications", e);
            }
        }).start();

        return mapToResponse(savedReview);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectReviewResponse> getProducerPublicPortfolio(Long producerId) {
        List<ProjectReview> reviews = reviewRepository.findPublicPortfolioByProducerId(producerId);

        return reviews.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectReviewResponse getProjectReview(Long projectId, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        ProjectReview review = reviewRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Chưa có đánh giá cho dự án này"));

        // Kiểm tra quyền xem: phải là client hoặc owner của project
        boolean isClient = project.getClient() != null && currentUser.getId().equals(project.getClient().getId());
        boolean isOwner = currentUser.getId().equals(project.getCreator().getId());

        if (!isClient && !isOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem đánh giá này");
        }

        return mapToResponse(review);
    }

    @Override
    @Transactional
    public ProjectReviewResponse updateReview(Long projectId, ProjectReviewRequest request, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ProjectReview review = reviewRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đánh giá"));

        // Chỉ client đã tạo review mới được cập nhật
        if (!currentUser.getId().equals(review.getReviewer().getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền cập nhật đánh giá này");
        }

        // Cập nhật thông tin
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setAllowPublicPortfolio(Boolean.TRUE.equals(request.getAllowPublicPortfolio()));

        ProjectReview savedReview = reviewRepository.save(review);
        log.info("Client {} updated review for Project {}", currentUser.getEmail(), projectId);

        // Gửi notification cho Producer (Owner) và Client về update (async)
        Long producerId = review.getProject().getCreator().getId();
        String producerEmail = review.getProject().getCreator().getEmail();
        Long clientId = currentUser.getId();
        String clientName = currentUser.getFullName();
        String producerName = review.getProject().getCreator().getFullName();
        String projectTitle = review.getProject().getTitle();
        Long projectIdForNotif = review.getProject().getId();
        
        log.info("Preparing to send update notifications - ProducerId: {}, ClientId: {}, ProjectId: {}", 
                producerId, clientId, projectIdForNotif);
        
        new Thread(() -> {
            try {
                Thread.sleep(500);
                log.info("Sending update notifications after transaction commit...");
                sendReviewUpdateNotificationToProducer(producerId, producerEmail, clientName, projectTitle, projectIdForNotif);
                sendReviewUpdateConfirmationToClient(clientId, producerName, projectTitle);
            } catch (Exception e) {
                log.error("Failed to send update notifications", e);
            }
        }).start();

        return mapToResponse(savedReview);
    }

    @Override
    @Transactional
    public void deleteReview(Long projectId, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ProjectReview review = reviewRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đánh giá"));

        // Chỉ client đã tạo review mới được xóa
        if (!currentUser.getId().equals(review.getReviewer().getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xóa đánh giá này");
        }

        Long producerId = review.getProject().getCreator().getId();
        String producerEmail = review.getProject().getCreator().getEmail();
        String clientName = currentUser.getFullName();
        String projectTitle = review.getProject().getTitle();
        Long projectIdForNotif = review.getProject().getId();

        reviewRepository.delete(review);
        log.info("Client {} deleted review for Project {}", currentUser.getEmail(), projectId);

        log.info("Preparing to send delete notifications - ProducerId: {}, ClientId: {}, ProjectId: {}", 
                producerId, currentUser.getId(), projectIdForNotif);

        // Gửi notification cho Producer (Owner) và Client về delete (async)
        new Thread(() -> {
            try {
                Thread.sleep(500);
                log.info("Sending delete notifications after transaction commit...");
                sendReviewDeleteNotificationToProducer(producerId, producerEmail, clientName, projectTitle, projectIdForNotif);
                sendReviewDeleteConfirmationToClient(currentUser.getId(), projectTitle);
            } catch (Exception e) {
                log.error("Failed to send delete notifications", e);
            }
        }).start();
    }

    // Helper method: Gửi notification khi tạo review mới
    private void sendReviewNotificationToProducer(Long producerId, String producerEmail, String producerName, 
                                                   String clientName, String projectTitle, Long projectId, 
                                                   Integer rating, String comment) {
        try {
            log.info("Sending review notification to Producer - ID: {}, Email: {}, Project: {}", 
                    producerId, producerEmail, projectTitle);
            
            User producer = userRepository.findById(producerId)
                    .orElseThrow(() -> {
                        log.error("Producer not found with ID: {}", producerId);
                        return new AppException(ErrorCode.USER_NOT_FOUND);
                    });
            
            String starText = "⭐".repeat(rating);

            // 1. Gửi in-app notification
            log.info("Sending in-app notification to Producer ID: {}", producerId);
            notificationService.sendNotification(SendNotificationRequest.builder()
                    .userId(producerId)
                    .type(NotificationType.REVIEW_RECEIVED)
                    .title("🌟 Bạn nhận được đánh giá mới!")
                    .message(String.format("%s đã đánh giá dự án \"%s\" với %s %d sao", 
                            clientName, 
                            projectTitle, 
                            starText,
                            rating))
                    .relatedEntityType(RelatedEntityType.PROJECT)
                    .relatedEntityId(projectId) // Fix: Dùng projectId thay vì producerId
                    .actionUrl("/projectManage")
                    .build());
            log.info("In-app notification sent successfully to Producer ID: {}", producerId);

            // 2. Gửi email notification
            log.info("Sending email to Producer: {}", producerEmail);
            emailService.sendReviewReceivedEmail(
                    producer.getEmail(),
                    producer.getFullName(),
                    clientName,
                    projectTitle,
                    rating,
                    comment
            );
            log.info("Email sent successfully to Producer: {}", producerEmail);

            log.info("✅ Successfully sent all review notifications to Producer {} (ID: {}) for Project {}", 
                    producer.getEmail(), producerId, projectTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send review notifications to Producer ID: {}, Error: {}", 
                    producerId, e.getMessage(), e);
        }
    }

    // Helper method: Gửi notification khi update review
    private void sendReviewUpdateNotificationToProducer(Long producerId, String producerEmail, String clientName, 
                                                        String projectTitle, Long projectId) {
        try {
            log.info("Sending review update notification to Producer - ID: {}, Email: {}, Project: {}", 
                    producerId, producerEmail, projectTitle);
            
            notificationService.sendNotification(SendNotificationRequest.builder()
                    .userId(producerId)
                    .type(NotificationType.REVIEW_UPDATED)
                    .title("📝 Đánh giá đã được cập nhật")
                    .message(String.format("%s đã cập nhật đánh giá cho dự án \"%s\"", 
                            clientName, 
                            projectTitle))
                    .relatedEntityType(RelatedEntityType.PROJECT)
                    .relatedEntityId(projectId) // Fix: Dùng projectId thay vì producerId
                    .actionUrl("/projectManage")
                    .build());

            log.info("✅ Successfully sent review update notification to Producer {} (ID: {}) for Project {}", 
                    producerEmail, producerId, projectTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send review update notification to Producer ID: {}, Error: {}", 
                    producerId, e.getMessage(), e);
        }
    }

    // Helper method: Gửi notification khi delete review
    private void sendReviewDeleteNotificationToProducer(Long producerId, String producerEmail, String clientName, 
                                                         String projectTitle, Long projectId) {
        try {
            log.info("Sending review delete notification to Producer - ID: {}, Email: {}, Project: {}", 
                    producerId, producerEmail, projectTitle);
            
            notificationService.sendNotification(SendNotificationRequest.builder()
                    .userId(producerId)
                    .type(NotificationType.REVIEW_DELETED)
                    .title("🗑️ Đánh giá đã bị xóa")
                    .message(String.format("%s đã xóa đánh giá cho dự án \"%s\"", 
                            clientName, 
                            projectTitle))
                    .relatedEntityType(RelatedEntityType.PROJECT)
                    .relatedEntityId(projectId)
                    .actionUrl("/projectManage")
                    .build());

            log.info("✅ Successfully sent review delete notification to Producer {} (ID: {}) for Project {}", 
                    producerEmail, producerId, projectTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send review delete notification to Producer ID: {}, Error: {}", 
                    producerId, e.getMessage(), e);
        }
    }

    // Helper method: Gửi confirmation notification cho Client khi tạo review
    private void sendReviewConfirmationToClient(Long clientId, String producerName, String projectTitle, Integer rating) {
        try {
            User client = userRepository.findById(clientId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            
            String starText = "⭐".repeat(rating);

            // 1. Gửi in-app notification
            notificationService.sendNotification(SendNotificationRequest.builder()
                    .userId(clientId)
                    .type(NotificationType.SYSTEM)
                    .title("✅ Đánh giá đã được gửi thành công!")
                    .message(String.format("Bạn đã đánh giá dự án \"%s\" của %s với %s %d sao", 
                            projectTitle, 
                            producerName,
                            starText,
                            rating))
                    .relatedEntityType(RelatedEntityType.PROJECT)
                    .relatedEntityId(clientId)
                    .actionUrl("/projectManage")
                    .build());

            // 2. Gửi email confirmation
            emailService.sendReviewConfirmationEmail(
                    client.getEmail(),
                    client.getFullName(),
                    producerName,
                    projectTitle,
                    rating
            );

            log.info("Sent review confirmation to Client {} for Project {}", client.getEmail(), projectTitle);
        } catch (Exception e) {
            log.error("Failed to send review confirmation to client", e);
        }
    }

    // Helper method: Gửi confirmation cho Client khi update review
    private void sendReviewUpdateConfirmationToClient(Long clientId, String producerName, String projectTitle) {
        try {
            notificationService.sendNotification(SendNotificationRequest.builder()
                    .userId(clientId)
                    .type(NotificationType.SYSTEM)
                    .title("✅ Đánh giá đã được cập nhật")
                    .message(String.format("Bạn đã cập nhật đánh giá cho dự án \"%s\" của %s", 
                            projectTitle, 
                            producerName))
                    .relatedEntityType(RelatedEntityType.PROJECT)
                    .relatedEntityId(clientId)
                    .actionUrl("/projectManage")
                    .build());

            log.info("Sent review update confirmation to Client {} for Project {}", clientId, projectTitle);
        } catch (Exception e) {
            log.error("Failed to send review update confirmation to client", e);
        }
    }

    // Helper method: Gửi confirmation cho Client khi delete review
    private void sendReviewDeleteConfirmationToClient(Long clientId, String projectTitle) {
        try {
            notificationService.sendNotification(SendNotificationRequest.builder()
                    .userId(clientId)
                    .type(NotificationType.SYSTEM)
                    .title("✅ Đánh giá đã được xóa")
                    .message(String.format("Bạn đã xóa đánh giá cho dự án \"%s\"", projectTitle))
                    .relatedEntityType(RelatedEntityType.PROJECT)
                    .relatedEntityId(clientId)
                    .actionUrl("/projectManage")
                    .build());

            log.info("Sent review delete confirmation to Client {} for Project {}", clientId, projectTitle);
        } catch (Exception e) {
            log.error("Failed to send review delete confirmation to client", e);
        }
    }

    private ProjectReviewResponse mapToResponse(ProjectReview review) {
        java.time.LocalDateTime createdDate = null;
        if (review.getCreatedAt() != null) {
            createdDate = review.getCreatedAt().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        return ProjectReviewResponse.builder()
                .id(review.getId())
                .projectId(review.getProject().getId())
                .projectTitle(review.getProject().getTitle())
                .producerName(review.getTargetUser().getFullName())
                .rating(review.getRating())
                .comment(review.getComment())
                .allowPublicPortfolio(review.isAllowPublicPortfolio())
                .createdAt(createdDate)
                .build();
    }
}