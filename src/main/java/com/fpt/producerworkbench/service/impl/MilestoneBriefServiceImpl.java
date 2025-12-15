package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.MilestoneBriefBlockType;
import com.fpt.producerworkbench.common.MilestoneBriefScope;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.dto.request.ForwardBriefGroupRequest;
import com.fpt.producerworkbench.dto.request.MilestoneBriefBlockRequest;
import com.fpt.producerworkbench.dto.request.MilestoneBriefGroupRequest;
import com.fpt.producerworkbench.dto.request.MilestoneBriefUpsertRequest;
import com.fpt.producerworkbench.dto.response.MilestoneBriefBlockResponse;
import com.fpt.producerworkbench.dto.response.MilestoneBriefDetailResponse;
import com.fpt.producerworkbench.dto.response.MilestoneBriefGroupResponse;
import com.fpt.producerworkbench.dto.response.ProjectPermissionResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.MilestoneBriefBlockRepository;
import com.fpt.producerworkbench.repository.MilestoneBriefGroupRepository;
import com.fpt.producerworkbench.repository.MilestoneMemberRepository;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.repository.UserRepository;
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
    private final MilestoneBriefBlockRepository briefBlockRepository;
    private final ProjectPermissionService projectPermissionService;
    private final MilestoneMemberRepository milestoneMemberRepository;
    private final UserRepository userRepository;
    private final FileStorageService storage;

    private static final long MAX_SIZE = 20L * 1024 * 1024; // 20MB
    private static final Set<String> IMAGE_MIMES = Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp", "image/gif");
    private static final Set<String> AUDIO_MIMES = Set.of("audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/x-m4a", "audio/webm", "audio/ogg");


    @Override
    public String getBriefFileUrl(Long projectId, Long milestoneId, String fileKey, Authentication auth) {
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);

        var roleInfo = getRoleInfo(auth, projectId);
        if (roleInfo.userRole == null && roleInfo.projectRole == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (fileKey == null ||
                (!fileKey.startsWith("brief-images/") && !fileKey.startsWith("brief-audios/"))) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT, "File key không hợp lệ");
        }

        return storage.generatePresignedUrl(fileKey, false, null);
    }

    @Override
    @Transactional(readOnly = true)
    public MilestoneBriefDetailResponse getMilestoneBrief(Long projectId, Long milestoneId, Authentication auth) {
        log.info("[EXTERNAL] Get brief: projectId={}, milestoneId={}", projectId, milestoneId);
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);
        validateExternalReadPermission(auth, projectId);
        return buildDetailResponse(milestone, MilestoneBriefScope.EXTERNAL);
    }

    @Override
    public List<MilestoneBriefGroupResponse> createBriefGroups(Long projectId, Long milestoneId, MilestoneBriefUpsertRequest request, Authentication auth) {
        log.info("[EXTERNAL] Batch create (Append): projectId={}, milestoneId={}", projectId, milestoneId);
        
        // Step 1: Save data in separate transaction
        List<Long> savedGroupIds = doSaveBriefGroups(projectId, milestoneId, request, auth);
        
        // Step 2: Map to response (outside transaction)
        List<MilestoneBriefGroupResponse> responses = new ArrayList<>();
        for (Long groupId : savedGroupIds) {
            try {
                MilestoneBriefGroup group = briefGroupRepository.findById(groupId).orElse(null);
                if (group != null) {
                    responses.add(mapToGroupResponse(group));
                }
            } catch (Exception e) {
                log.error("[ERROR] Failed to map group {} to response: {}", groupId, e.getMessage());
                responses.add(MilestoneBriefGroupResponse.builder()
                        .id(groupId)
                        .blocks(Collections.emptyList())
                        .build());
            }
        }
        return responses;
    }
    
    @Transactional
    public List<Long> doSaveBriefGroups(Long projectId, Long milestoneId, MilestoneBriefUpsertRequest request, Authentication auth) {
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);
        validateExternalWritePermission(auth, projectId);


        List<Long> savedGroupIds = new ArrayList<>();
        if (request != null && request.getGroups() != null) {
            for (MilestoneBriefGroupRequest groupReq : request.getGroups()) {
                // Step 1: Create group WITHOUT blocks
                MilestoneBriefGroup group = MilestoneBriefGroup.builder()
                        .milestone(milestone)
                        .scope(MilestoneBriefScope.EXTERNAL)
                        .title(groupReq.getTitle())
                        .position(groupReq.getPosition() != null ? groupReq.getPosition() : 999)
                        .build();
                
                MilestoneBriefGroup savedGroup = briefGroupRepository.save(group);
                
                if (groupReq.getBlocks() != null && !groupReq.getBlocks().isEmpty()) {
                    List<MilestoneBriefBlock> blocks = mapBlocksRequestToEntity(groupReq.getBlocks(), savedGroup);
                    briefBlockRepository.saveAll(blocks);
                }
                
                savedGroupIds.add(savedGroup.getId());
            }
        }

        return savedGroupIds;
    }

    @Override
    @Transactional
    public MilestoneBriefDetailResponse upsertMilestoneBrief(Long projectId, Long milestoneId, MilestoneBriefUpsertRequest request, Authentication auth) {
        log.info("[EXTERNAL] Smart Upsert: projectId={}, milestoneId={}", projectId, milestoneId);
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);
        validateExternalWritePermission(auth, projectId);

        // 1. Get existing groups
        List<MilestoneBriefGroup> currentGroups = briefGroupRepository.findByMilestoneIdAndScopeOrderByPositionAsc(milestoneId, MilestoneBriefScope.EXTERNAL);
        
        // 2. QUAN TRỌNG: Xóa forwardId của các group INTERNAL trước khi xóa group EXTERNAL
        // Đảm bảo 2 phòng độc lập - group INTERNAL vẫn tồn tại sau khi xóa EXTERNAL
        for (MilestoneBriefGroup group : currentGroups) {
            Optional<MilestoneBriefGroup> internalGroupOpt = briefGroupRepository.findByForwardIdAndScope(group.getId(), MilestoneBriefScope.INTERNAL);
            if (internalGroupOpt.isPresent()) {
                MilestoneBriefGroup internalGroup = internalGroupOpt.get();
                internalGroup.setForwardId(null); // Xóa liên kết, giữ lại group INTERNAL
                briefGroupRepository.save(internalGroup);
                log.info("Đã xóa forwardId của group INTERNAL {} để đảm bảo độc lập với group EXTERNAL {}", 
                        internalGroup.getId(), group.getId());
            }
        }
        
        // 3. Delete ALL existing blocks and groups first (clean slate approach)
        for (MilestoneBriefGroup group : currentGroups) {
            briefBlockRepository.deleteAllByGroupId(group.getId());
        }
        briefGroupRepository.deleteAll(currentGroups);
        briefGroupRepository.flush(); // Ensure deletes are committed
        
        // 3. Create new groups and blocks
        if (request != null && request.getGroups() != null) {
            int position = 1;
            for (MilestoneBriefGroupRequest req : request.getGroups()) {
                MilestoneBriefGroup group = MilestoneBriefGroup.builder()
                        .milestone(milestone)
                        .scope(MilestoneBriefScope.EXTERNAL)
                        .title(req.getTitle())
                        .position(req.getPosition() != null ? req.getPosition() : position++)
                        .build();
                
                MilestoneBriefGroup savedGroup = briefGroupRepository.save(group);
                
                if (req.getBlocks() != null && !req.getBlocks().isEmpty()) {
                    List<MilestoneBriefBlock> blocks = mapBlocksRequestToEntity(req.getBlocks(), savedGroup);
                    briefBlockRepository.saveAll(blocks);
                }
            }
        }

        return buildDetailResponse(milestone, MilestoneBriefScope.EXTERNAL);
    }

    @Override
    @Transactional
    public void deleteBriefGroup(Long projectId, Long milestoneId, Long groupId, Authentication auth) {
        log.info("[EXTERNAL] Delete group: projectId={}, groupId={}", projectId, groupId);
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);
        validateExternalWritePermission(auth, projectId);
        MilestoneBriefGroup group = findGroupAndValidateBelonging(groupId, milestoneId, MilestoneBriefScope.EXTERNAL);
        
        // QUAN TRỌNG: Đảm bảo 2 phòng độc lập
        // Tìm group INTERNAL có forwardId trỏ đến group này và xóa forwardId để cắt liên kết
        // Nhưng KHÔNG xóa group INTERNAL - giữ nguyên dữ liệu ở phòng nội bộ
        Optional<MilestoneBriefGroup> internalGroupOpt = briefGroupRepository.findByForwardIdAndScope(groupId, MilestoneBriefScope.INTERNAL);
        if (internalGroupOpt.isPresent()) {
            MilestoneBriefGroup internalGroup = internalGroupOpt.get();
            internalGroup.setForwardId(null); // Xóa liên kết, nhưng giữ lại group INTERNAL
            briefGroupRepository.save(internalGroup);
            log.info("Đã xóa forwardId của group INTERNAL {} (ID: {}) để đảm bảo độc lập với group EXTERNAL {}", 
                    internalGroup.getId(), internalGroup.getId(), groupId);
        }
        
        // Delete blocks first (no cascade)
        if (group.getBlocks() != null && !group.getBlocks().isEmpty()) {
            briefBlockRepository.deleteAll(group.getBlocks());
        }
        briefGroupRepository.delete(group);
        log.info("Đã xóa group EXTERNAL {} - không ảnh hưởng đến group INTERNAL", groupId);
    }


    @Override
    @Transactional(readOnly = true)
    public MilestoneBriefDetailResponse getInternalMilestoneBrief(Long projectId, Long milestoneId, Authentication auth) {
        log.info("[INTERNAL] Get brief: projectId={}, milestoneId={}", projectId, milestoneId);
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);
        validateInternalReadPermission(auth, projectId, milestoneId);
        return buildDetailResponse(milestone, MilestoneBriefScope.INTERNAL);
    }

    @Override
    @Transactional
    public MilestoneBriefDetailResponse upsertInternalMilestoneBrief(Long projectId, Long milestoneId, MilestoneBriefUpsertRequest request, Authentication auth) {
        log.info("[INTERNAL] Upsert ALL: projectId={}, milestoneId={}", projectId, milestoneId);
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);
        validateOwnerOnly(auth, projectId);

        // Delete existing groups and their blocks
        List<MilestoneBriefGroup> existingGroups = briefGroupRepository.findByMilestoneIdAndScopeOrderByPositionAsc(milestoneId, MilestoneBriefScope.INTERNAL);
        for (MilestoneBriefGroup group : existingGroups) {
            if (group.getBlocks() != null && !group.getBlocks().isEmpty()) {
                briefBlockRepository.deleteAll(group.getBlocks());
            }
        }
        briefGroupRepository.deleteAll(existingGroups);

        // Create new groups and blocks
        if (request != null && request.getGroups() != null && !request.getGroups().isEmpty()) {
            for (MilestoneBriefGroupRequest groupReq : request.getGroups()) {
                // Save group first
                MilestoneBriefGroup group = MilestoneBriefGroup.builder()
                        .milestone(milestone)
                        .scope(MilestoneBriefScope.INTERNAL)
                        .title(groupReq.getTitle())
                        .position(groupReq.getPosition() != null ? groupReq.getPosition() : 999)
                        .build();
                
                MilestoneBriefGroup savedGroup = briefGroupRepository.save(group);
                
                // Then save blocks
                if (groupReq.getBlocks() != null && !groupReq.getBlocks().isEmpty()) {
                    List<MilestoneBriefBlock> blocks = mapBlocksRequestToEntity(groupReq.getBlocks(), savedGroup);
                    briefBlockRepository.saveAll(blocks);
                }
            }
        }
        return buildDetailResponse(milestone, MilestoneBriefScope.INTERNAL);
    }

    @Override
    @Transactional
    public void deleteInternalBriefGroup(Long projectId, Long milestoneId, Long groupId, Authentication auth) {
        log.info("[INTERNAL] Delete group: projectId={}, groupId={}", projectId, groupId);
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);
        validateOwnerOnly(auth, projectId);
        MilestoneBriefGroup group = findGroupAndValidateBelonging(groupId, milestoneId, MilestoneBriefScope.INTERNAL);
        
        // Delete blocks first (no cascade)
        if (group.getBlocks() != null && !group.getBlocks().isEmpty()) {
            briefBlockRepository.deleteAll(group.getBlocks());
        }
        briefGroupRepository.delete(group);
    }


    @Override
    @Transactional
    public MilestoneBriefGroupResponse forwardExternalGroupToInternal(Long projectId, Long milestoneId, Long groupId, Authentication auth) {
        log.info("Forwarding/Syncing Group ID {} from EXTERNAL to INTERNAL", groupId);
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);
        validateOwnerOnly(auth, projectId);

        MilestoneBriefGroup sourceGroup = findGroupAndValidateBelonging(groupId, milestoneId, MilestoneBriefScope.EXTERNAL);

        // Tìm bản sao cũ trong Internal
        Optional<MilestoneBriefGroup> existingGroupOpt = briefGroupRepository.findByForwardIdAndScope(groupId, MilestoneBriefScope.INTERNAL);
        MilestoneBriefGroup targetGroup;

        if (existingGroupOpt.isPresent()) {
            // CÓ RỒI -> UPDATE
            targetGroup = existingGroupOpt.get();
            targetGroup.setTitle(sourceGroup.getTitle());
            targetGroup.setPosition(sourceGroup.getPosition());
            
            // Delete old blocks manually
            if (targetGroup.getBlocks() != null && !targetGroup.getBlocks().isEmpty()) {
                briefBlockRepository.deleteAll(targetGroup.getBlocks());
            }
            briefGroupRepository.save(targetGroup);
        } else {
            // CHƯA CÓ -> TẠO MỚI và save trước
            targetGroup = MilestoneBriefGroup.builder()
                    .milestone(milestone)
                    .scope(MilestoneBriefScope.INTERNAL)
                    .forwardId(groupId)
                    .title(sourceGroup.getTitle())
                    .position(sourceGroup.getPosition())
                    .build();
            targetGroup = briefGroupRepository.save(targetGroup);
        }

        // Clone Blocks - save manually
        List<MilestoneBriefBlock> clonedBlocks = new ArrayList<>();
        if (sourceGroup.getBlocks() != null && !sourceGroup.getBlocks().isEmpty()) {
            for (MilestoneBriefBlock sourceBlock : sourceGroup.getBlocks()) {
                MilestoneBriefBlock targetBlock = MilestoneBriefBlock.builder()
                        .group(targetGroup)
                        .type(sourceBlock.getType())
                        .label(sourceBlock.getLabel())
                        .contentText(sourceBlock.getContentText())
                        .fileKey(sourceBlock.getFileKey())
                        .position(sourceBlock.getPosition())
                        .build();
                clonedBlocks.add(targetBlock);
            }
            briefBlockRepository.saveAll(clonedBlocks);
        }
        
        targetGroup.setBlocks(clonedBlocks); // Set for response mapping
        return mapToGroupResponse(targetGroup);
    }

    @Override
    @Transactional
    public MilestoneBriefGroupResponse forwardExternalGroupToInternalWithEdit(
            Long projectId, Long milestoneId, Long groupId, ForwardBriefGroupRequest request, Authentication auth) {
        log.info("Forwarding Group ID {} from EXTERNAL to INTERNAL with edit: projectId={}, milestoneId={}", 
                groupId, projectId, milestoneId);
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);
        validateOwnerOnly(auth, projectId);

        MilestoneBriefGroup sourceGroup = findGroupAndValidateBelonging(groupId, milestoneId, MilestoneBriefScope.EXTERNAL);

        // Tìm bản sao cũ trong Internal
        Optional<MilestoneBriefGroup> existingGroupOpt = briefGroupRepository.findByForwardIdAndScope(groupId, MilestoneBriefScope.INTERNAL);
        MilestoneBriefGroup targetGroup;

        // Xác định title và position: dùng từ request nếu có, không thì dùng từ source
        String targetTitle = (request != null && request.getGroup() != null && request.getGroup().getTitle() != null)
                ? request.getGroup().getTitle()
                : sourceGroup.getTitle();
        
        Integer targetPosition = (request != null && request.getGroup() != null && request.getGroup().getPosition() != null)
                ? request.getGroup().getPosition()
                : sourceGroup.getPosition();

        if (existingGroupOpt.isPresent()) {
            // CÓ RỒI -> UPDATE
            targetGroup = existingGroupOpt.get();
            targetGroup.setTitle(targetTitle);
            targetGroup.setPosition(targetPosition);
            
            // Delete old blocks manually
            if (targetGroup.getBlocks() != null && !targetGroup.getBlocks().isEmpty()) {
                briefBlockRepository.deleteAll(targetGroup.getBlocks());
            }
            briefGroupRepository.save(targetGroup);
        } else {
            // CHƯA CÓ -> TẠO MỚI
            targetGroup = MilestoneBriefGroup.builder()
                    .milestone(milestone)
                    .scope(MilestoneBriefScope.INTERNAL)
                    .forwardId(groupId)
                    .title(targetTitle)
                    .position(targetPosition)
                    .build();
            targetGroup = briefGroupRepository.save(targetGroup);
        }

        // Clone Blocks: dùng từ request nếu có, không thì clone từ source
        List<MilestoneBriefBlock> clonedBlocks = new ArrayList<>();
        
        if (request != null && request.getGroup() != null && request.getGroup().getBlocks() != null 
                && !request.getGroup().getBlocks().isEmpty()) {
            // Dùng blocks từ request (đã chỉnh sửa)
            clonedBlocks = mapBlocksRequestToEntity(request.getGroup().getBlocks(), targetGroup);
            briefBlockRepository.saveAll(clonedBlocks);
        } else if (sourceGroup.getBlocks() != null && !sourceGroup.getBlocks().isEmpty()) {
            // Clone từ source (nguyên bản)
            for (MilestoneBriefBlock sourceBlock : sourceGroup.getBlocks()) {
                MilestoneBriefBlock targetBlock = MilestoneBriefBlock.builder()
                        .group(targetGroup)
                        .type(sourceBlock.getType())
                        .label(sourceBlock.getLabel())
                        .contentText(sourceBlock.getContentText())
                        .fileKey(sourceBlock.getFileKey())
                        .position(sourceBlock.getPosition())
                        .build();
                clonedBlocks.add(targetBlock);
            }
            briefBlockRepository.saveAll(clonedBlocks);
        }
        
        targetGroup.setBlocks(clonedBlocks); // Set for response mapping
        return mapToGroupResponse(targetGroup);
    }

    @Override
    @Transactional
    public String uploadBriefFile(Long projectId, Long milestoneId, MultipartFile file, String type, Authentication auth) {
        Milestone milestone = loadMilestoneAndCheckAuth(projectId, milestoneId, auth);
        validateExternalWritePermission(auth, projectId);

        if (file == null || file.isEmpty()) throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        if (file.getSize() > MAX_SIZE) throw new AppException(ErrorCode.FILE_TOO_LARGE);

        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String folder = "brief-files";
        if ("IMAGE".equalsIgnoreCase(type)) {
            if (!IMAGE_MIMES.contains(mime)) throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            folder = "brief-images";
        } else if ("AUDIO".equalsIgnoreCase(type) || "HUM_MELODY".equalsIgnoreCase(type)) {
            if (!AUDIO_MIMES.contains(mime) && !mime.startsWith("audio/")) throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            folder = "brief-audios";
        }
        String key = folder + "/" + projectId + "_" + milestoneId + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        storage.uploadFile(file, key);
        return key;
    }

    private void validateExternalReadPermission(Authentication auth, Long projectId) {
        var roleInfo = getRoleInfo(auth, projectId);
        boolean isOwner = roleInfo.userRole == UserRole.PRODUCER && roleInfo.projectRole == ProjectRole.OWNER;
        boolean isClient = roleInfo.userRole == UserRole.CUSTOMER && roleInfo.projectRole == ProjectRole.CLIENT;
        boolean isObserver = roleInfo.projectRole == ProjectRole.OBSERVER;

        if (!isOwner && !isClient && !isObserver) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem nội dung ngoại bộ");
        }
    }

    private void validateExternalWritePermission(Authentication auth, Long projectId) {
        var roleInfo = getRoleInfo(auth, projectId);
        boolean isOwner = roleInfo.userRole == UserRole.PRODUCER && roleInfo.projectRole == ProjectRole.OWNER;
        boolean isClient = roleInfo.projectRole == ProjectRole.CLIENT;

        if (!isOwner && !isClient) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa nội dung ngoại bộ");
        }
    }

    private void validateOwnerOnly(Authentication auth, Long projectId) {
        var roleInfo = getRoleInfo(auth, projectId);
        boolean isOwner = roleInfo.userRole == UserRole.PRODUCER && roleInfo.projectRole == ProjectRole.OWNER;
        if (!isOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Chỉ Owner mới có quyền thực hiện thao tác này");
        }
    }

    private void validateInternalReadPermission(Authentication auth, Long projectId, Long milestoneId) {
        var roleInfo = getRoleInfo(auth, projectId);

        if (roleInfo.userRole == UserRole.PRODUCER && roleInfo.projectRole == ProjectRole.OWNER) return;

        if (roleInfo.projectRole == ProjectRole.COLLABORATOR || roleInfo.projectRole == ProjectRole.OBSERVER) {
            User currentUser = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            boolean isMember = milestoneMemberRepository.existsByMilestoneIdAndUserId(milestoneId, currentUser.getId());
            if (!isMember) throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn chưa được thêm vào cột mốc này");
            return;
        }

        throw new AppException(ErrorCode.ACCESS_DENIED);
    }

    private Milestone loadMilestoneAndCheckAuth(Long projectId, Long milestoneId, Authentication auth) {
        if (auth == null || auth.getName() == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        Milestone milestone = milestoneRepository.findById(milestoneId).orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));
        if (!milestone.getContract().getProject().getId().equals(projectId)) throw new AppException(ErrorCode.ACCESS_DENIED);
        return milestone;
    }

    private RoleInfo getRoleInfo(Authentication auth, Long projectId) {
        ProjectPermissionResponse permission = projectPermissionService.checkProjectPermissions(auth, projectId);
        var role = permission.getRole();
        return new RoleInfo(role != null ? role.getUserRole() : null, role != null ? role.getProjectRole() : null);
    }

    private MilestoneBriefGroup findGroupAndValidateBelonging(Long groupId, Long milestoneId, MilestoneBriefScope scope) {
        MilestoneBriefGroup group = briefGroupRepository.findById(groupId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!group.getMilestone().getId().equals(milestoneId) || group.getScope() != scope) throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        return group;
    }

    private MilestoneBriefGroup createGroupEntity(Milestone milestone, MilestoneBriefGroupRequest req, MilestoneBriefScope scope) {
        MilestoneBriefGroup group = MilestoneBriefGroup.builder()
                .milestone(milestone)
                .scope(scope)
                .title(req.getTitle())
                .position(req.getPosition() != null ? req.getPosition() : 999)
                .blocks(new ArrayList<>()) // Khởi tạo rõ ràng thay vì dùng @Builder.Default
                .build();
        
        if (req.getBlocks() != null && !req.getBlocks().isEmpty()) {
            // Sử dụng addAll để thêm vào collection hiện tại thay vì thay thế
            group.getBlocks().addAll(mapBlocksRequestToEntity(req.getBlocks(), group));
        }
        return group;
    }

    private List<MilestoneBriefBlock> mapBlocksRequestToEntity(List<MilestoneBriefBlockRequest> blockReqs, MilestoneBriefGroup group) {
        List<MilestoneBriefBlock> blocks = new ArrayList<>();
        int index = 1;
        for (MilestoneBriefBlockRequest br : blockReqs) {
            String fileKey = (br.getType() == MilestoneBriefBlockType.IMAGE || br.getType() == MilestoneBriefBlockType.HUM_MELODY) ? br.getContent() : null;
            String text = (fileKey == null) ? br.getContent() : null;
            String label = (br.getLabel() != null && !br.getLabel().isBlank()) ? br.getLabel() : defaultLabelByType(br.getType());
            blocks.add(MilestoneBriefBlock.builder().group(group).type(br.getType()).label(label).contentText(text).fileKey(fileKey).position(br.getPosition() != null ? br.getPosition() : index++).build());
        }
        return blocks;
    }

    private MilestoneBriefDetailResponse buildDetailResponse(Milestone milestone, MilestoneBriefScope scope) {
        List<MilestoneBriefGroup> groups = briefGroupRepository.findByMilestoneIdAndScopeOrderByPositionAsc(milestone.getId(), scope);
        return MilestoneBriefDetailResponse.builder().milestoneId(milestone.getId()).projectId(milestone.getContract().getProject().getId()).scope(scope).groups(groups.stream().map(this::mapToGroupResponse).collect(Collectors.toList())).build();
    }

    private MilestoneBriefGroupResponse mapToGroupResponse(MilestoneBriefGroup group) {
        List<MilestoneBriefBlockResponse> blockResponses = group.getBlocks() == null ? Collections.emptyList() :
                group.getBlocks().stream().sorted(Comparator.comparing(MilestoneBriefBlock::getPosition, Comparator.nullsLast(Integer::compareTo))).map(this::mapToBlockResponse).collect(Collectors.toList());
        return MilestoneBriefGroupResponse.builder().id(group.getId()).title(group.getTitle()).position(group.getPosition()).blocks(blockResponses).build();
    }

    private MilestoneBriefBlockResponse mapToBlockResponse(MilestoneBriefBlock block) {
        String content = block.getContentText();
        if (block.getFileKey() != null) {
            try {
                content = storage.generatePresignedUrl(block.getFileKey(), false, null);
            } catch (Exception e) {
                log.warn("[WARN] Failed to generate presigned URL for fileKey: {}, error: {}", 
                    block.getFileKey(), e.getMessage());
                content = block.getFileKey(); // Fallback to raw key
            }
        }
        return MilestoneBriefBlockResponse.builder().id(block.getId()).type(block.getType()).label(block.getLabel()).content(content).position(block.getPosition()).build();
    }

    private String defaultLabelByType(MilestoneBriefBlockType type) {
        if (type == null) return null;
        return switch (type) { case DESCRIPTION -> "Miêu tả"; case HARMONY -> "Hòa âm"; case IMAGE -> "Ảnh"; case HUM_MELODY -> "Ngân nga giai điệu"; };
    }

    private static class RoleInfo { final UserRole userRole; final ProjectRole projectRole; RoleInfo(UserRole u, ProjectRole p) { this.userRole = u; this.projectRole = p; }}
}