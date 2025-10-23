package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.ProjectCreateRequest;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

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
                .isFunded(false)
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

        return savedProject;
    }
}