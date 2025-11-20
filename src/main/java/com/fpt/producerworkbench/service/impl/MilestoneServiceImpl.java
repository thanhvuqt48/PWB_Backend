package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.MilestoneStatus;
import com.fpt.producerworkbench.common.MoneySplitStatus;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.AddMilestoneMemberRequest;
import com.fpt.producerworkbench.dto.request.MilestoneRequest;
import com.fpt.producerworkbench.dto.response.AvailableProjectMemberResponse;
import com.fpt.producerworkbench.dto.response.MilestoneListResponse;
import com.fpt.producerworkbench.dto.response.MilestoneResponse;
import com.fpt.producerworkbench.dto.response.MilestoneDetailResponse;
import com.fpt.producerworkbench.dto.response.MilestoneMemberResponse;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.entity.MilestoneMember;
import com.fpt.producerworkbench.entity.MilestoneMoneySplit;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.repository.MilestoneMemberRepository;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.repository.MilestoneMoneySplitRepository;
import com.fpt.producerworkbench.service.MilestoneService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import com.fpt.producerworkbench.common.ConversationType;
import com.fpt.producerworkbench.dto.request.ConversationCreationRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MilestoneServiceImpl implements MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final ContractRepository contractRepository;
    private final ProjectPermissionService projectPermissionService;
    private final MilestoneMemberRepository milestoneMemberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final MilestoneMoneySplitRepository milestoneMoneySplitRepository;
    private final ConversationService conversationService;

    private static final String NOTIFICATION_TOPIC = "notification-delivery";

    @Override
    public List<MilestoneListResponse> getAllMilestonesByProject(Long projectId, Authentication auth) {
        log.info("Lấy danh sách cột mốc cho dự án: {}", projectId);

        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Contract contract = contractRepository.findByProjectIdWithProject(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        Project project = contract.getProject();
        Long ownerId = project.getCreator() != null ? project.getCreator().getId() : null;
        Long clientId = project.getClient() != null ? project.getClient().getId() : null;

        String projectTitle = project.getTitle();
        Integer contractProductCount = contract.getProductCount();
        Integer contractFpEditCount = contract.getFpEditAmount();
        BigDecimal contractTotalAmount = contract.getTotalAmount();

        List<Milestone> milestones = milestoneRepository.findByContractIdOrderBySequenceAsc(contract.getId());

        boolean isOwner = ownerId != null && currentUser.getId().equals(ownerId);
        boolean isClient = clientId != null && currentUser.getId().equals(clientId);

        if (isOwner || isClient) {
            return milestones.stream()
                    .map(m -> mapToListResponse(m, projectTitle, contractProductCount, contractFpEditCount,
                            contractTotalAmount))
                    .collect(Collectors.toList());
        }

        List<Long> milestoneIds = milestones.stream()
                .map(Milestone::getId)
                .collect(Collectors.toList());

        Set<Long> userMilestoneIds = milestoneMemberRepository
                .findByMilestoneIdInAndUserId(milestoneIds, currentUser.getId())
                .stream()
                .map(mm -> mm.getMilestone() != null ? mm.getMilestone().getId() : null)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        return milestones.stream()
                .filter(m -> userMilestoneIds.contains(m.getId()))
                .map(m -> mapToListResponse(m, projectTitle, contractProductCount, contractFpEditCount,
                        contractTotalAmount))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MilestoneResponse createMilestone(Long projectId, MilestoneRequest request, Authentication auth) {
        log.info("Tạo cột mốc mới cho dự án: {}", projectId);

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isCanCreateMilestone()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Contract contract = contractRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        Project project = contract.getProject();
        if (project == null || !Boolean.TRUE.equals(project.getIsFunded())) {
            throw new AppException(ErrorCode.PROJECT_NOT_FUNDED);
        }

        if (PaymentType.MILESTONE.equals(contract.getPaymentType())) {
            throw new AppException(ErrorCode.CANNOT_CREATE_MILESTONE_FOR_MILESTONE_PAYMENT_TYPE);
        }
        if (!PaymentType.FULL.equals(contract.getPaymentType())) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_TYPE);
        }

        if (!ContractStatus.COMPLETED.equals(contract.getStatus())) {
            throw new AppException(ErrorCode.CONTRACT_NOT_COMPLETED_FOR_MILESTONE);
        }

        validateMilestoneRequest(contract, request, null);

        List<Milestone> existingMilestones = milestoneRepository.findByContractIdOrderBySequenceAsc(contract.getId());
        int nextSequence = existingMilestones.size() + 1;

        BigDecimal amount = new BigDecimal(request.getAmount());

        Milestone milestone = Milestone.builder()
                .contract(contract)
                .title(request.getTitle())
                .description(request.getDescription())
                .amount(amount)
                .dueDate(request.getDueDate())
                .status(MilestoneStatus.PENDING)
                .sequence(nextSequence)
                .editCount(request.getEditCount())
                .productCount(request.getProductCount())
                .build();

        Milestone saved = milestoneRepository.save(milestone);

        if (project != null) {
            User owner = project.getCreator();
            User client = project.getClient();

            if (owner != null
                    && !milestoneMemberRepository.existsByMilestoneIdAndUserId(saved.getId(), owner.getId())) {
                MilestoneMember ownerMember = MilestoneMember.builder()
                        .milestone(saved)
                        .user(owner)
                        .build();
                milestoneMemberRepository.save(ownerMember);
            }

            if (client != null) {
                boolean sameAsOwner = owner != null && owner.getId().equals(client.getId());
                if (!sameAsOwner
                        && !milestoneMemberRepository.existsByMilestoneIdAndUserId(saved.getId(), client.getId())) {
                    MilestoneMember clientMember = MilestoneMember.builder()
                            .milestone(saved)
                            .user(client)
                            .build();
                    milestoneMemberRepository.save(clientMember);
                }
            }

            if (Boolean.TRUE.equals(request.getCreateInternalGroupChat())) {
                if (request.getInternalGroupChatName() == null || request.getInternalGroupChatName().trim().isEmpty()) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Tên group chat nội bộ không được để trống");
                }
                if (owner != null) {
                    try {
                        ConversationCreationRequest internalChatRequest = ConversationCreationRequest.builder()
                                .conversationType(ConversationType.GROUP)
                                .conversationName(request.getInternalGroupChatName())
                                .participantIds(List.of(owner.getId()))
                                .build();
                        conversationService.create(internalChatRequest);
                        log.info("Đã tạo group chat nội bộ cho milestone: {}", saved.getId());
                    } catch (Exception e) {
                        log.error("Lỗi khi tạo group chat nội bộ cho milestone {}: {}", saved.getId(), e.getMessage());
                    }
                }
            }

            if (Boolean.TRUE.equals(request.getCreateClientGroupChat())) {
                if (request.getClientGroupChatName() == null || request.getClientGroupChatName().trim().isEmpty()) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Tên group chat với khách hàng không được để trống");
                }
                if (owner != null && client != null) {
                    boolean sameAsOwner = owner.getId().equals(client.getId());
                    if (!sameAsOwner) {
                        try {
                            ConversationCreationRequest clientChatRequest = ConversationCreationRequest.builder()
                                    .conversationType(ConversationType.GROUP)
                                    .conversationName(request.getClientGroupChatName())
                                    .participantIds(List.of(owner.getId(), client.getId()))
                                    .build();
                            conversationService.create(clientChatRequest);
                            log.info("Đã tạo group chat với khách hàng cho milestone: {}", saved.getId());
                        } catch (Exception e) {
                            log.error("Lỗi khi tạo group chat với khách hàng cho milestone {}: {}", saved.getId(),
                                    e.getMessage());
                        }
                    } else {
                        log.warn(
                                "Không thể tạo group chat với khách hàng vì owner và client là cùng một người cho milestone: {}",
                                saved.getId());
                    }
                } else {
                    log.warn("Không thể tạo group chat với khách hàng vì thiếu owner hoặc client cho milestone: {}",
                            saved.getId());
                }
            }
        }

        log.info("Tạo cột mốc thành công với ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public MilestoneResponse updateMilestone(Long projectId, Long milestoneId, MilestoneRequest request,
            Authentication auth) {
        log.info("Cập nhật cột mốc: projectId={}, milestoneId={}", projectId, milestoneId);

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        if (milestone.getContract() == null || milestone.getContract().getProject() == null
                || !milestone.getContract().getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Contract contract = milestone.getContract();

        if (PaymentType.MILESTONE.equals(contract.getPaymentType())) {
            throw new AppException(ErrorCode.CANNOT_CREATE_MILESTONE_FOR_MILESTONE_PAYMENT_TYPE);
        }
        if (!PaymentType.FULL.equals(contract.getPaymentType())) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_TYPE);
        }

        if (!ContractStatus.COMPLETED.equals(contract.getStatus())) {
            throw new AppException(ErrorCode.CONTRACT_NOT_COMPLETED_FOR_MILESTONE);
        }

        validateMilestoneRequest(contract, request, milestoneId);

        BigDecimal amount = new BigDecimal(request.getAmount());
        validateExistingMoneySplits(milestone, amount);

        milestone.setTitle(request.getTitle());
        milestone.setDescription(request.getDescription());
        milestone.setAmount(amount);
        milestone.setDueDate(request.getDueDate());
        milestone.setEditCount(request.getEditCount());
        milestone.setProductCount(request.getProductCount());

        Milestone saved = milestoneRepository.save(milestone);

        log.info("Cập nhật cột mốc thành công: milestoneId={}", saved.getId());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MilestoneDetailResponse getMilestoneDetail(Long projectId, Long milestoneId, Authentication auth) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        if (milestone.getContract() == null || milestone.getContract().getProject() == null
                || !milestone.getContract().getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Project project = milestone.getContract().getProject();
        Long ownerId = project.getCreator() != null ? project.getCreator().getId() : null;
        Long clientId = project.getClient() != null ? project.getClient().getId() : null;

        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User currentUser = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean isOwner = ownerId != null && currentUser.getId().equals(ownerId);
        boolean isClient = clientId != null && currentUser.getId().equals(clientId);
        boolean isMilestoneMember = milestoneMemberRepository.existsByMilestoneIdAndUserId(milestoneId,
                currentUser.getId());

        if (!isOwner && !isClient && !isMilestoneMember) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Optional<ProjectMember> currentUserProjectMember = projectMemberRepository
                .findByProjectIdAndUserId(projectId, currentUser.getId());
        boolean isCurrentUserAnonymous = currentUserProjectMember.isPresent()
                && currentUserProjectMember.get().getProjectRole() == ProjectRole.COLLABORATOR
                && currentUserProjectMember.get().isAnonymous();

        List<ProjectMember> projectMembers = projectMemberRepository.findByProjectId(projectId);
        Map<Long, ProjectRole> projectMemberRoleMap = projectMembers.stream()
                .filter(pm -> pm.getUser() != null)
                .collect(Collectors.toMap(
                        pm -> pm.getUser().getId(),
                        ProjectMember::getProjectRole));

        Map<Long, Boolean> userAnonymousMap = projectMembers.stream()
                .filter(pm -> pm.getUser() != null)
                .collect(Collectors.toMap(
                        pm -> pm.getUser().getId(),
                        pm -> pm.getProjectRole() == ProjectRole.COLLABORATOR && pm.isAnonymous()));

        var members = milestoneMemberRepository.findByMilestoneId(milestoneId).stream()
                .filter(mm -> {
                    if (mm.getUser() == null)
                        return false;
                    Long userId = mm.getUser().getId();

                    if (isOwner) {
                        return true;
                    }

                    if (isCurrentUserAnonymous) {
                        return ownerId != null && userId.equals(ownerId);
                    }

                    boolean isMemberAnonymous = userAnonymousMap.getOrDefault(userId, false);
                    return !isMemberAnonymous;
                })
                .map(mm -> {
                    Long userId = mm.getUser() != null ? mm.getUser().getId() : null;
                    String role = determineMemberRole(userId, ownerId, clientId, projectMemberRoleMap);
                    boolean isMemberAnonymous = userAnonymousMap.getOrDefault(userId, false);

                    return MilestoneMemberResponse.builder()
                            .id(mm.getId())
                            .userId(userId)
                            .userName(mm.getUser() != null ? mm.getUser().getFullName() : null)
                            .userEmail(mm.getUser() != null ? mm.getUser().getEmail() : null)
                            .description(mm.getDescription())
                            .role(role)
                            .isAnonymous(isMemberAnonymous)
                            .build();
                })
                .collect(Collectors.toList());

        LocalDateTime createdAt = milestone.getCreatedAt() != null
                ? milestone.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null;
        LocalDateTime updatedAt = milestone.getUpdatedAt() != null
                ? milestone.getUpdatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null;

        return MilestoneDetailResponse.builder()
                .id(milestone.getId())
                .contractId(milestone.getContract() != null ? milestone.getContract().getId() : null)
                .title(milestone.getTitle())
                .description(milestone.getDescription())
                .amount(milestone.getAmount())
                .dueDate(milestone.getDueDate())
                .status(milestone.getStatus())
                .editCount(milestone.getEditCount())
                .productCount(milestone.getProductCount())
                .sequence(milestone.getSequence())
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .members(members)
                .build();
    }

    private void validateMilestoneRequest(Contract contract, MilestoneRequest request, Long excludeMilestoneId) {
        Optional<Milestone> existingMilestoneWithSameTitle = milestoneRepository
                .findByContractIdAndTitleIgnoreCase(contract.getId(), request.getTitle());
        if (existingMilestoneWithSameTitle.isPresent()
                && (excludeMilestoneId == null
                        || !existingMilestoneWithSameTitle.get().getId().equals(excludeMilestoneId))) {
            throw new AppException(ErrorCode.MILESTONE_TITLE_DUPLICATE);
        }

        if (request.getEditCount() != null && contract.getFpEditAmount() != null) {
            if (request.getEditCount() > contract.getFpEditAmount()) {
                int maxAllowed = contract.getFpEditAmount();
                throw new AppException(
                        ErrorCode.EDIT_COUNT_EXCEEDS_CONTRACT_LIMIT,
                        String.format("Số lượt chỉnh sửa yêu cầu (%d) vượt quá giới hạn theo hợp đồng (%d).",
                                request.getEditCount(), maxAllowed));
            }
        }

        if (request.getProductCount() != null && contract.getProductCount() != null) {
            if (request.getProductCount() > contract.getProductCount()) {
                int maxAllowed = contract.getProductCount();
                throw new AppException(
                        ErrorCode.PRODUCT_COUNT_EXCEEDS_CONTRACT_LIMIT,
                        String.format("Số lượng sản phẩm yêu cầu (%d) vượt quá giới hạn theo hợp đồng (%d).",
                                request.getProductCount(), maxAllowed));
            }
        }

        List<Milestone> existingMilestones = milestoneRepository.findByContractIdOrderBySequenceAsc(contract.getId());
        if (excludeMilestoneId != null) {
            existingMilestones = existingMilestones.stream()
                    .filter(m -> !m.getId().equals(excludeMilestoneId))
                    .collect(Collectors.toList());
        }
        BigDecimal existingTotal = existingMilestones.stream()
                .map(Milestone::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newAmount = new BigDecimal(request.getAmount());
        BigDecimal totalAmount = existingTotal.add(newAmount);

        if (totalAmount.compareTo(contract.getTotalAmount()) > 0) {
            BigDecimal contractTotal = contract.getTotalAmount();
            BigDecimal remaining = contractTotal.subtract(existingTotal);
            throw new AppException(
                    ErrorCode.MILESTONE_AMOUNT_EXCEEDS_CONTRACT_TOTAL,
                    String.format("Số tiền cột mốc yêu cầu (%s) vượt quá số tiền còn lại (%s) trong hợp đồng.",
                            formatAmount(newAmount), formatAmount(remaining.max(BigDecimal.ZERO))));
        }

        if (request.getEditCount() != null && contract.getFpEditAmount() != null) {
            int existingEditCount = existingMilestones.stream()
                    .mapToInt(m -> m.getEditCount() != null ? m.getEditCount() : 0)
                    .sum();
            int totalEditCount = existingEditCount + request.getEditCount();
            if (totalEditCount > contract.getFpEditAmount()) {
                int remainingEditCount = contract.getFpEditAmount() - existingEditCount;
                throw new AppException(
                        ErrorCode.EDIT_COUNT_TOTAL_EXCEEDS_CONTRACT_LIMIT,
                        String.format(
                                "Tổng số lượt chỉnh sửa (%d) vượt quá giới hạn hợp đồng (%d). Số lượt còn lại: %d.",
                                totalEditCount, contract.getFpEditAmount(), Math.max(remainingEditCount, 0)));
            }
        }

        if (request.getProductCount() != null && contract.getProductCount() != null) {
            int existingProductCount = existingMilestones.stream()
                    .mapToInt(m -> m.getProductCount() != null ? m.getProductCount() : 0)
                    .sum();
            int totalProductCount = existingProductCount + request.getProductCount();
            if (totalProductCount > contract.getProductCount()) {
                int remainingProductCount = contract.getProductCount() - existingProductCount;
                throw new AppException(
                        ErrorCode.PRODUCT_COUNT_TOTAL_EXCEEDS_CONTRACT_LIMIT,
                        String.format(
                                "Tổng số lượng sản phẩm (%d) vượt quá giới hạn hợp đồng (%d). Số lượng còn lại: %d.",
                                totalProductCount, contract.getProductCount(), Math.max(remainingProductCount, 0)));
            }
        }
    }

    private void validateExistingMoneySplits(Milestone milestone, BigDecimal newAmount) {
        List<MilestoneMoneySplit> moneySplits = milestoneMoneySplitRepository.findByMilestoneId(milestone.getId());
        BigDecimal totalSplit = moneySplits.stream()
                .map(MilestoneMoneySplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalSplit.compareTo(newAmount) > 0) {
            throw new AppException(
                    ErrorCode.MONEY_SPLIT_TOTAL_EXCEEDS_MILESTONE,
                    String.format("Tổng số tiền đã phân chia (%s) vượt quá số tiền cột mốc hiện tại (%s).",
                            formatAmount(totalSplit), formatAmount(newAmount)));
        }
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private void resequenceMilestonesAfterDeletion(Long contractId) {
        List<Milestone> milestones = milestoneRepository.findByContractIdOrderBySequenceAsc(contractId);
        if (milestones.isEmpty()) {
            return;
        }

        List<Milestone> sorted = milestones.stream()
                .sorted(Comparator.comparing(Milestone::getSequence, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());

        int sequence = 1;
        for (Milestone milestone : sorted) {
            milestone.setSequence(sequence++);
        }

        milestoneRepository.saveAll(sorted);
    }

    private MilestoneListResponse mapToListResponse(Milestone milestone, String projectTitle,
            Integer contractProductCount, Integer contractFpEditCount, BigDecimal contractTotalAmount) {
        return MilestoneListResponse.builder()
                .id(milestone.getId())
                .title(milestone.getTitle())
                .description(milestone.getDescription())
                .status(milestone.getStatus())
                .sequence(milestone.getSequence())
                .productCount(milestone.getProductCount())
                .editCount(milestone.getEditCount())
                .amount(milestone.getAmount())
                .contractProductCount(contractProductCount)
                .contractFpEditCount(contractFpEditCount)
                .contractTotalAmount(contractTotalAmount)
                .projectTitle(projectTitle)
                .createdAt(milestone.getCreatedAt())
                .updatedAt(milestone.getUpdatedAt())
                .build();
    }

    private MilestoneResponse mapToResponse(Milestone milestone) {
        return MilestoneResponse.builder()
                .id(milestone.getId())
                .title(milestone.getTitle())
                .description(milestone.getDescription())
                .amount(milestone.getAmount())
                .dueDate(milestone.getDueDate())
                .status(milestone.getStatus())
                .editCount(milestone.getEditCount())
                .productCount(milestone.getProductCount())
                .sequence(milestone.getSequence())
                .createdAt(milestone.getCreatedAt())
                .updatedAt(milestone.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableProjectMemberResponse> getAvailableProjectMembers(Long projectId, Long milestoneId,
            Authentication auth) {
        log.info("Lấy danh sách thành viên dự án có thể thêm vào cột mốc: projectId={}, milestoneId={}", projectId,
                milestoneId);

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        if (milestone.getContract() == null || milestone.getContract().getProject() == null
                || !milestone.getContract().getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Project project = milestone.getContract().getProject();

        List<MilestoneMember> currentMilestoneMembers = milestoneMemberRepository.findByMilestoneId(milestoneId);
        Set<Long> currentMemberUserIds = currentMilestoneMembers.stream()
                .map(mm -> mm.getUser() != null ? mm.getUser().getId() : null)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Long ownerId = project.getCreator() != null ? project.getCreator().getId() : null;
        Long clientId = project.getClient() != null ? project.getClient().getId() : null;

        List<ProjectMember> projectMembers = projectMemberRepository.findByProjectId(projectId);

        return projectMembers.stream()
                .filter(pm -> {
                    if (pm.getUser() == null)
                        return false;
                    Long userId = pm.getUser().getId();

                    if (ownerId != null && userId.equals(ownerId))
                        return false;
                    if (clientId != null && userId.equals(clientId))
                        return false;

                    if (currentMemberUserIds.contains(userId))
                        return false;

                    ProjectRole role = pm.getProjectRole();
                    return role == ProjectRole.COLLABORATOR || role == ProjectRole.OBSERVER;
                })
                .map(pm -> AvailableProjectMemberResponse.builder()
                        .userId(pm.getUser().getId())
                        .userName(pm.getUser().getFullName())
                        .userEmail(pm.getUser().getEmail())
                        .projectRole(pm.getProjectRole() != null ? pm.getProjectRole().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MilestoneDetailResponse addMembersToMilestone(Long projectId, Long milestoneId,
            AddMilestoneMemberRequest request, Authentication auth) {
        log.info("Thêm thành viên vào cột mốc: projectId={}, milestoneId={}, members={}", projectId, milestoneId,
                request.getMembers());

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        if (milestone.getContract() == null || milestone.getContract().getProject() == null
                || !milestone.getContract().getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Project project = milestone.getContract().getProject();

        if (request.getMembers() == null || request.getMembers().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        List<MilestoneMember> currentMilestoneMembers = milestoneMemberRepository.findByMilestoneId(milestoneId);
        Set<Long> currentMemberUserIds = currentMilestoneMembers.stream()
                .map(mm -> mm.getUser() != null ? mm.getUser().getId() : null)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        for (var memberRequest : request.getMembers()) {
            Long userId = memberRequest.getUserId();
            String description = memberRequest.getDescription();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            Optional<ProjectMember> projectMemberOpt = projectMemberRepository.findByProjectIdAndUserId(projectId,
                    userId);
            if (projectMemberOpt.isEmpty()) {
                throw new AppException(ErrorCode.USER_NOT_IN_PROJECT);
            }

            ProjectMember projectMember = projectMemberOpt.get();
            ProjectRole projectRole = projectMember.getProjectRole();

            Long ownerId = project.getCreator() != null ? project.getCreator().getId() : null;
            Long clientId = project.getClient() != null ? project.getClient().getId() : null;

            if (ownerId != null && userId.equals(ownerId)) {
                throw new AppException(ErrorCode.MEMBER_ALREADY_IN_MILESTONE);
            }
            if (clientId != null && userId.equals(clientId)) {
                throw new AppException(ErrorCode.MEMBER_ALREADY_IN_MILESTONE);
            }

            if (currentMemberUserIds.contains(userId)) {
                throw new AppException(ErrorCode.MEMBER_ALREADY_IN_MILESTONE);
            }

            if (projectRole != ProjectRole.COLLABORATOR && projectRole != ProjectRole.OBSERVER) {
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }

            MilestoneMember milestoneMember = MilestoneMember.builder()
                    .milestone(milestone)
                    .user(user)
                    .description(description)
                    .build();
            milestoneMemberRepository.save(milestoneMember);
            currentMemberUserIds.add(userId);

            log.info("Đã thêm thành viên userId={} với mô tả '{}' vào milestone milestoneId={}", userId, description,
                    milestoneId);

            sendMilestoneMemberNotificationEmail(user, project, milestone, description, projectRole);
        }

        return getMilestoneDetail(projectId, milestoneId, auth);
    }

    @Override
    @Transactional
    public MilestoneDetailResponse removeMemberFromMilestone(Long projectId, Long milestoneId, Long userId,
            Authentication auth) {
        log.info("Xóa thành viên khỏi cột mốc: projectId={}, milestoneId={}, userId={}", projectId, milestoneId,
                userId);

        if (userId == null || userId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (permission == null || !permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        if (milestone.getContract() == null || milestone.getContract().getProject() == null
                || !milestone.getContract().getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Project project = milestone.getContract().getProject();
        Long ownerId = project.getCreator() != null ? project.getCreator().getId() : null;
        Long clientId = project.getClient() != null ? project.getClient().getId() : null;

        if (ownerId != null && ownerId.equals(userId)) {
            throw new AppException(ErrorCode.PROJECT_OWNER_CANNOT_BE_MODIFIED);
        }

        if (clientId != null && clientId.equals(userId)) {
            throw new AppException(ErrorCode.PROJECT_CLIENT_CANNOT_BE_MODIFIED);
        }

        MilestoneMember milestoneMember = milestoneMemberRepository.findByMilestoneIdAndUserId(milestoneId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_MEMBER_NOT_FOUND));

        List<MilestoneMoneySplit> userSplits = milestoneMoneySplitRepository.findByMilestoneIdAndUserId(milestoneId,
                userId);
        boolean hasApprovedSplit = userSplits.stream()
                .anyMatch(split -> split.getStatus() == MoneySplitStatus.APPROVED);

        if (hasApprovedSplit) {
            throw new AppException(ErrorCode.MILESTONE_MEMBER_HAS_APPROVED_MONEY_SPLIT);
        }

        if (!userSplits.isEmpty()) {
            milestoneMoneySplitRepository.deleteAll(userSplits);
        }

        User removedUser = milestoneMember.getUser();
        milestoneMemberRepository.delete(milestoneMember);
        sendMilestoneMemberRemovalEmail(removedUser, project, milestone);

        log.info("Đã xóa thành viên userId={} khỏi milestone {}", userId, milestoneId);

        return getMilestoneDetail(projectId, milestoneId, auth);
    }

    @Override
    @Transactional
    public void deleteMilestone(Long projectId, Long milestoneId, Authentication auth) {
        log.info("Xóa cột mốc: projectId={}, milestoneId={}", projectId, milestoneId);

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        if (milestone.getContract() == null || milestone.getContract().getProject() == null
                || !milestone.getContract().getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        boolean hasApprovedMoneySplit = milestoneMoneySplitRepository
                .existsByMilestoneIdAndStatus(milestoneId, MoneySplitStatus.APPROVED);
        if (hasApprovedMoneySplit) {
            throw new AppException(ErrorCode.MILESTONE_HAS_APPROVED_MONEY_SPLIT);
        }

        var moneySplits = milestoneMoneySplitRepository.findByMilestoneId(milestoneId);
        if (!moneySplits.isEmpty()) {
            milestoneMoneySplitRepository.deleteAll(moneySplits);
        }

        var milestoneMembers = milestoneMemberRepository.findByMilestoneId(milestoneId);
        if (!milestoneMembers.isEmpty()) {
            milestoneMemberRepository.deleteAll(milestoneMembers);
        }

        Long contractId = milestone.getContract().getId();
        milestoneRepository.delete(milestone);
        resequenceMilestonesAfterDeletion(contractId);

        log.info("Đã xóa cột mốc thành công: milestoneId={}", milestoneId);
    }

    private String determineMemberRole(Long userId, Long ownerId, Long clientId,
            Map<Long, ProjectRole> projectMemberRoleMap) {
        if (userId == null) {
            return null;
        }

        if (ownerId != null && userId.equals(ownerId)) {
            return ProjectRole.OWNER.name();
        }

        if (clientId != null && userId.equals(clientId)) {
            return ProjectRole.CLIENT.name();
        }

        ProjectRole projectRole = projectMemberRoleMap.get(userId);
        if (projectRole != null) {
            return projectRole.name();
        }

        return null;
    }

    private void sendMilestoneMemberNotificationEmail(User user, Project project, Milestone milestone,
            String description, ProjectRole projectRole) {
        try {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                log.warn("Không thể gửi email thông báo: user {} không có email", user.getId());
                return;
            }

            String projectUrl = String.format("http://localhost:5173/projects/%d/milestones/%d",
                    project.getId(), milestone.getId());

            String roleName = projectRole != null ? projectRole.name() : "MEMBER";

            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", user.getFullName() != null ? user.getFullName() : user.getEmail());
            params.put("projectName", project.getTitle());
            params.put("milestoneTitle", milestone.getTitle());
            params.put("role", roleName);
            params.put("projectUrl", projectUrl);

            if (description != null && !description.trim().isEmpty()) {
                params.put("description", description);
            }

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(user.getEmail())
                    .subject("Bạn đã được thêm vào Cột mốc: " + milestone.getTitle())
                    .templateCode("milestone-member-added-template")
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo thêm thành viên vào milestone qua Kafka: userId={}, milestoneId={}",
                    user.getId(), milestone.getId());

        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo thêm thành viên vào milestone qua Kafka: userId={}, milestoneId={}",
                    user.getId(), milestone.getId(), e);
        }
    }

    private void sendMilestoneMemberRemovalEmail(User removedUser, Project project, Milestone milestone) {
        if (removedUser == null) {
            return;
        }

        try {
            if (removedUser.getEmail() == null || removedUser.getEmail().isBlank()) {
                log.warn("Không thể gửi email thông báo xóa thành viên khỏi milestone vì user {} không có email",
                        removedUser.getId());
                return;
            }

            String projectUrl = String.format("http://localhost:5173/projects/%d/milestones/%d",
                    project.getId(), milestone.getId());

            Map<String, Object> params = new HashMap<>();
            params.put("recipientName",
                    removedUser.getFullName() != null ? removedUser.getFullName() : removedUser.getEmail());
            params.put("projectName", project.getTitle());
            params.put("milestoneTitle", milestone.getTitle());
            params.put("projectUrl", projectUrl);

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(removedUser.getEmail())
                    .subject("Bạn đã bị xóa khỏi Cột mốc: " + milestone.getTitle())
                    .templateCode("milestone-member-removed-template")
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo xóa thành viên khỏi milestone qua Kafka: userId={}, milestoneId={}",
                    removedUser.getId(), milestone.getId());

        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo xóa thành viên khỏi milestone qua Kafka: userId={}, milestoneId={}",
                    removedUser.getId(), milestone.getId(), e);
        }
    }
}
