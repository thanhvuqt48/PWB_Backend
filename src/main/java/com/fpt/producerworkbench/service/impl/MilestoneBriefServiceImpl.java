package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.MilestoneBriefBlockType;
import com.fpt.producerworkbench.common.MilestoneBriefScope;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.dto.request.MilestoneBriefUpsertRequest;
import com.fpt.producerworkbench.dto.request.MilestoneBriefGroupRequest;
import com.fpt.producerworkbench.dto.request.MilestoneBriefBlockRequest;
import com.fpt.producerworkbench.dto.response.MilestoneBriefDetailResponse;
import com.fpt.producerworkbench.dto.response.MilestoneBriefGroupResponse;
import com.fpt.producerworkbench.dto.response.MilestoneBriefBlockResponse;
import com.fpt.producerworkbench.dto.response.ProjectPermissionResponse;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.entity.MilestoneBriefGroup;
import com.fpt.producerworkbench.entity.MilestoneBriefBlock;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.MilestoneBriefGroupRepository;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.MilestoneBriefService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MilestoneBriefServiceImpl implements MilestoneBriefService {

    private final MilestoneRepository milestoneRepository;
    private final MilestoneBriefGroupRepository briefGroupRepository;
    private final ProjectPermissionService projectPermissionService;
    private final FileStorageService storage;

    private static final long MAX_SIZE = 20L * 1024 * 1024; // 20MB
    private static final Set<String> IMAGE_MIMES = Set.of(
            MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp", "image/gif"
    );
    private static final Set<String> AUDIO_MIMES = Set.of(
            "audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/x-m4a", "audio/webm", "audio/ogg"
    );


    @Override
    @Transactional
    public MilestoneBriefDetailResponse upsertMilestoneBrief(Long projectId, Long milestoneId,
                                                             MilestoneBriefUpsertRequest request,
                                                             Authentication auth) {
        log.info("[EXTERNAL] Upsert milestone brief: projectId={}, milestoneId={}", projectId, milestoneId);

        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);

        var roleInfo = getRoleInfo(auth, projectId);
        UserRole userRole = roleInfo.userRole;
        ProjectRole projectRole = roleInfo.projectRole;

        boolean isCustomerOfProject = userRole == UserRole.CUSTOMER && projectRole == ProjectRole.CLIENT;
        if (!isCustomerOfProject) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        briefGroupRepository.deleteByMilestoneIdAndScope(milestoneId, MilestoneBriefScope.EXTERNAL);

        List<MilestoneBriefGroup> groupsToSave =
                buildGroupsFromRequest(milestone, MilestoneBriefScope.EXTERNAL, request);

        if (!groupsToSave.isEmpty()) {
            briefGroupRepository.saveAll(groupsToSave);
        }

        List<MilestoneBriefGroup> savedGroups =
                briefGroupRepository.findByMilestoneIdAndScopeOrderByPositionAsc(milestoneId, MilestoneBriefScope.EXTERNAL);

        return buildDetailResponse(milestone, savedGroups, MilestoneBriefScope.EXTERNAL);
    }

    @Override
    @Transactional(readOnly = true)
    public MilestoneBriefDetailResponse getMilestoneBrief(Long projectId, Long milestoneId,
                                                          Authentication auth) {
        log.info("[EXTERNAL] Get milestone brief: projectId={}, milestoneId={}", projectId, milestoneId);

        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);

        var roleInfo = getRoleInfo(auth, projectId);
        UserRole userRole = roleInfo.userRole;
        ProjectRole projectRole = roleInfo.projectRole;

        boolean isProducerOwner = userRole == UserRole.PRODUCER && projectRole == ProjectRole.OWNER;
        boolean isCustomerClient = userRole == UserRole.CUSTOMER && projectRole == ProjectRole.CLIENT;

        if (!isProducerOwner && !isCustomerClient) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        List<MilestoneBriefGroup> groups =
                briefGroupRepository.findByMilestoneIdAndScopeOrderByPositionAsc(milestoneId, MilestoneBriefScope.EXTERNAL);

        return buildDetailResponse(milestone, groups, MilestoneBriefScope.EXTERNAL);
    }

    @Override
    @Transactional
    public void deleteExternalMilestoneBrief(Long projectId, Long milestoneId, Authentication auth) {
        log.info("[EXTERNAL] Delete milestone brief: projectId={}, milestoneId={}", projectId, milestoneId);

        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);

        var roleInfo = getRoleInfo(auth, projectId);
        ProjectRole projectRole = roleInfo.projectRole;

        boolean isClient = projectRole == ProjectRole.CLIENT;
        if (!isClient) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        briefGroupRepository.deleteByMilestoneIdAndScope(milestone.getId(), MilestoneBriefScope.EXTERNAL);
        log.info("[EXTERNAL] Deleted all EXTERNAL brief groups for milestoneId={}", milestone.getId());
    }

    @Override
    @Transactional
    public MilestoneBriefDetailResponse upsertInternalMilestoneBrief(Long projectId, Long milestoneId,
                                                                     MilestoneBriefUpsertRequest request,
                                                                     Authentication auth) {
        log.info("[INTERNAL] Upsert milestone brief: projectId={}, milestoneId={}", projectId, milestoneId);

        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);

        var roleInfo = getRoleInfo(auth, projectId);
        UserRole userRole = roleInfo.userRole;
        ProjectRole projectRole = roleInfo.projectRole;

        boolean isProducerOwner = userRole == UserRole.PRODUCER && projectRole == ProjectRole.OWNER;
        if (!isProducerOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        briefGroupRepository.deleteByMilestoneIdAndScope(milestoneId, MilestoneBriefScope.INTERNAL);

        List<MilestoneBriefGroup> groupsToSave =
                buildGroupsFromRequest(milestone, MilestoneBriefScope.INTERNAL, request);

        if (!groupsToSave.isEmpty()) {
            briefGroupRepository.saveAll(groupsToSave);
        }

        List<MilestoneBriefGroup> savedGroups =
                briefGroupRepository.findByMilestoneIdAndScopeOrderByPositionAsc(milestoneId, MilestoneBriefScope.INTERNAL);

        return buildDetailResponse(milestone, savedGroups, MilestoneBriefScope.INTERNAL);
    }


    @Override
    @Transactional(readOnly = true)
    public MilestoneBriefDetailResponse getInternalMilestoneBrief(Long projectId, Long milestoneId,
                                                                  Authentication auth) {
        log.info("[INTERNAL] Get milestone brief: projectId={}, milestoneId={}", projectId, milestoneId);

        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);

        var roleInfo = getRoleInfo(auth, projectId);
        UserRole userRole = roleInfo.userRole;
        ProjectRole projectRole = roleInfo.projectRole;

        boolean isOwner = projectRole == ProjectRole.OWNER;
        boolean isCollaborator = projectRole == ProjectRole.COLLABORATOR;

        if (!isOwner && !isCollaborator) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }


        List<MilestoneBriefGroup> groups =
                briefGroupRepository.findByMilestoneIdAndScopeOrderByPositionAsc(milestoneId, MilestoneBriefScope.INTERNAL);

        return buildDetailResponse(milestone, groups, MilestoneBriefScope.INTERNAL);
    }

    @Override
    @Transactional
    public void deleteInternalMilestoneBrief(Long projectId, Long milestoneId, Authentication auth) {
        log.info("[INTERNAL] Delete milestone brief: projectId={}, milestoneId={}", projectId, milestoneId);

        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);

        var roleInfo = getRoleInfo(auth, projectId);
        ProjectRole projectRole = roleInfo.projectRole;

        boolean isOwner = projectRole == ProjectRole.OWNER;
        if (!isOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        briefGroupRepository.deleteByMilestoneIdAndScope(milestone.getId(), MilestoneBriefScope.INTERNAL);
        log.info("[INTERNAL] Deleted all INTERNAL brief groups for milestoneId={}", milestone.getId());
    }

    @Override
    @Transactional
    public String uploadBriefFile(Long projectId, Long milestoneId, MultipartFile file, String type, Authentication auth) {
        log.info("Upload brief file: projectId={}, milestoneId={}, type={}", projectId, milestoneId, type);

        // 1. Kiểm tra tồn tại milestone
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);

        // 2. Kiểm tra quyền (Cho phép cả Client và Owner upload, quyền chi tiết sẽ chặn ở lúc Lưu Brief)
        var roleInfo = getRoleInfo(auth, projectId);
        boolean isClient = roleInfo.userRole == UserRole.CUSTOMER && roleInfo.projectRole == ProjectRole.CLIENT;
        boolean isProducerOwner = roleInfo.userRole == UserRole.PRODUCER && roleInfo.projectRole == ProjectRole.OWNER;

        if (!isClient && !isProducerOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // 3. Validate File
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String folder;

        if ("IMAGE".equalsIgnoreCase(type)) {
            if (!IMAGE_MIMES.contains(mime)) {
                throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Chỉ hỗ trợ ảnh (JPG, PNG, WEBP, GIF)");
            }
            folder = "brief-images";
        } else if ("AUDIO".equalsIgnoreCase(type) || "HUM_MELODY".equalsIgnoreCase(type)) {
            // Chấp nhận mime audio hoặc mime bắt đầu bằng audio/
            if (!AUDIO_MIMES.contains(mime) && !mime.startsWith("audio/")) {
                throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Định dạng âm thanh không hỗ trợ");
            }
            folder = "brief-audios";
        } else {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "Type phải là IMAGE hoặc AUDIO");
        }

        // 4. Upload
        String key = folder + "/" + projectId + "_" + milestoneId + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        storage.uploadFile(file, key);

        return key; // Trả về key để FE lưu vào JSON
    }

    private Milestone loadMilestoneAndCheckAuth(Long projectId, Long milestoneId, Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        if (milestone.getContract() == null || milestone.getContract().getProject() == null
                || !milestone.getContract().getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        return milestone;
    }

    private static class RoleInfo {
        final UserRole userRole;
        final ProjectRole projectRole;

        RoleInfo(UserRole userRole, ProjectRole projectRole) {
            this.userRole = userRole;
            this.projectRole = projectRole;
        }
    }

    private RoleInfo getRoleInfo(Authentication auth, Long projectId) {
        ProjectPermissionResponse permission = projectPermissionService.checkProjectPermissions(auth, projectId);
        var role = permission.getRole();

        UserRole userRole = role != null ? role.getUserRole() : null;
        ProjectRole projectRole = role != null ? role.getProjectRole() : null;

        return new RoleInfo(userRole, projectRole);
    }

    private List<MilestoneBriefGroup> buildGroupsFromRequest(Milestone milestone,
                                                             MilestoneBriefScope scope,
                                                             MilestoneBriefUpsertRequest request) {
        List<MilestoneBriefGroup> groupsToSave = new ArrayList<>();

        if (request != null && request.getGroups() != null) {
            int groupIndex = 1;

            for (MilestoneBriefGroupRequest groupReq : request.getGroups()) {
                if (groupReq == null) continue;

                MilestoneBriefGroup group = MilestoneBriefGroup.builder()
                        .milestone(milestone)
                        .scope(scope)
                        .title(groupReq.getTitle())
                        .position(groupReq.getPosition() != null
                                ? groupReq.getPosition()
                                : groupIndex++)
                        .build();

                if (groupReq.getBlocks() != null && !groupReq.getBlocks().isEmpty()) {
                    List<MilestoneBriefBlock> blocks = new ArrayList<>();
                    int blockIndex = 1;

                    for (MilestoneBriefBlockRequest blockReq : groupReq.getBlocks()) {
                        if (blockReq == null || blockReq.getType() == null) continue;

                        MilestoneBriefBlockType type = blockReq.getType();
                        String label = blockReq.getLabel();
                        if (label == null || label.isBlank()) {
                            label = defaultLabelByType(type);
                        }

                        String contentText = null;
                        String fileKey = null;

                        // MỚI: Phân loại Content vs FileKey dựa trên Type
                        // Frontend sẽ gửi key nhận được từ API upload vào field 'content' của request
                        if (type == MilestoneBriefBlockType.IMAGE || type == MilestoneBriefBlockType.HUM_MELODY) {
                            fileKey = blockReq.getContent();
                        } else {
                            contentText = blockReq.getContent();
                        }

                        MilestoneBriefBlock block = MilestoneBriefBlock.builder()
                                .group(group)
                                .type(type)
                                .label(label)
                                .contentText(contentText)
                                .fileKey(fileKey)
                                .position(blockReq.getPosition() != null
                                        ? blockReq.getPosition()
                                        : blockIndex++)
                                .build();

                        blocks.add(block);
                    }
                    group.setBlocks(blocks);
                }

                groupsToSave.add(group);
            }
        }

        return groupsToSave;
    }

    private MilestoneBriefDetailResponse buildDetailResponse(Milestone milestone,
                                                             List<MilestoneBriefGroup> groups,
                                                             MilestoneBriefScope scope) {
        List<MilestoneBriefGroupResponse> groupResponses = groups == null
                ? Collections.emptyList()
                : groups.stream()
                .sorted(Comparator.comparing(
                        MilestoneBriefGroup::getPosition,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(this::mapToGroupResponse)
                .collect(Collectors.toList());

        Long projectId = milestone.getContract() != null && milestone.getContract().getProject() != null
                ? milestone.getContract().getProject().getId()
                : null;

        return MilestoneBriefDetailResponse.builder()
                .milestoneId(milestone.getId())
                .projectId(projectId)
                .scope(scope)
                .groups(groupResponses)
                .build();
    }

    private MilestoneBriefGroupResponse mapToGroupResponse(MilestoneBriefGroup group) {
        List<MilestoneBriefBlockResponse> blockResponses = group.getBlocks() == null
                ? Collections.emptyList()
                : group.getBlocks().stream()
                .sorted(Comparator.comparing(
                        MilestoneBriefBlock::getPosition,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(this::mapToBlockResponse)
                .collect(Collectors.toList());

        return MilestoneBriefGroupResponse.builder()
                .id(group.getId())
                .title(group.getTitle())
                .position(group.getPosition())
                .blocks(blockResponses)
                .build();
    }

    private MilestoneBriefBlockResponse mapToBlockResponse(MilestoneBriefBlock block) {
        String content;

        if (block.getType() == MilestoneBriefBlockType.IMAGE || block.getType() == MilestoneBriefBlockType.HUM_MELODY) {
            String fileKey = block.getFileKey();
            if (fileKey != null && !fileKey.isBlank()) {
                content = storage.generatePresignedUrl(fileKey, false, null);
            } else {
                content = "";
            }
        } else {
            content = block.getContentText();
        }

        return MilestoneBriefBlockResponse.builder()
                .id(block.getId())
                .type(block.getType())
                .label(block.getLabel())
                .content(content)
                .position(block.getPosition())
                .build();
    }

    private String defaultLabelByType(MilestoneBriefBlockType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case DESCRIPTION -> "Miêu tả";
            case HARMONY -> "Hòa âm";
            case IMAGE -> "Ảnh";
            case HUM_MELODY -> "Ngân nga giai điệu";
        };
    }


}
