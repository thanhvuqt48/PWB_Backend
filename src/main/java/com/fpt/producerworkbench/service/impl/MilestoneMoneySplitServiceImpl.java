package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.configuration.FrontendProperties;
import com.fpt.producerworkbench.common.MoneySplitStatus;
import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.*;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.dto.response.MilestoneExpenseResponse;
import com.fpt.producerworkbench.dto.response.MilestoneMoneySplitDetailResponse;
import com.fpt.producerworkbench.dto.response.MilestoneMoneySplitResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.MilestoneMoneySplitService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import com.fpt.producerworkbench.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MilestoneMoneySplitServiceImpl implements MilestoneMoneySplitService {

    private final MilestoneRepository milestoneRepository;
    private final MilestoneMoneySplitRepository moneySplitRepository;
    private final MilestoneExpenseRepository expenseRepository;
    private final MilestoneMemberRepository milestoneMemberRepository;
    private final UserRepository userRepository;
    private final ProjectPermissionService projectPermissionService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final NotificationService notificationService;
    private final FrontendProperties frontendProperties;

    private static final String NOTIFICATION_TOPIC = "notification-delivery";

    @Override
    @Transactional
    public MilestoneMoneySplitResponse createMoneySplit(Long projectId, Long milestoneId,
            CreateMoneySplitRequest request, Authentication auth) {
        log.info("Tạo phân chia tiền: projectId={}, milestoneId={}, userId={}", projectId, milestoneId,
                request.getUserId());

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Milestone milestone = validateMilestone(projectId, milestoneId);

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project project = milestone.getContract().getProject();
        Long ownerId = project.getCreator() != null ? project.getCreator().getId() : null;
        Long clientId = project.getClient() != null ? project.getClient().getId() : null;

        if (ownerId != null && request.getUserId().equals(ownerId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (clientId != null && request.getUserId().equals(clientId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        boolean isMember = milestoneMemberRepository.existsByMilestoneIdAndUserId(milestoneId, request.getUserId());
        if (!isMember) {
            throw new AppException(ErrorCode.USER_NOT_IN_PROJECT);
        }

        BigDecimal amount = new BigDecimal(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        validateTotalAmountForExpense(milestone, amount, null);

        // Kiểm tra milestone đã completed thì không được tạo phân chia tiền nữa
        if (milestone.getStatus() == com.fpt.producerworkbench.common.MilestoneStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cột mốc đã hoàn thành, không thể tạo phân chia tiền");
        }

        MilestoneMoneySplit moneySplit = MilestoneMoneySplit.builder()
                .milestone(milestone)
                .user(user)
                .amount(amount)
                .status(MoneySplitStatus.PENDING)
                .note(request.getNote())
                .build();

        MilestoneMoneySplit saved = moneySplitRepository.save(moneySplit);

        log.info("Đã tạo phân chia tiền: moneySplitId={}, userId={}, amount={}",
                saved.getId(), request.getUserId(), amount);

        sendMoneySplitNotificationEmail(user, project, milestone, amount, request.getNote(), "created");

        try {
            User currentUser = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            String amountStr = amount != null ? amount.stripTrailingZeros().toPlainString() : "0";
            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(request.getUserId())
                            .type(NotificationType.MONEY_SPLIT_REQUEST)
                            .title("Lời mời chấp nhận chia tiền")
                            .message(String.format(
                                    "%s đã đề xuất chia tiền %s cho bạn trong milestone \"%s\" của dự án \"%s\"%s",
                                    currentUser.getFullName() != null ? currentUser.getFullName()
                                            : currentUser.getEmail(),
                                    amountStr,
                                    milestone.getTitle(),
                                    project.getTitle(),
                                    request.getNote() != null && !request.getNote().isBlank()
                                            ? " - " + request.getNote()
                                            : ""))
                            .relatedEntityType(RelatedEntityType.MONEY_SPLIT)
                            .relatedEntityId(saved.getId())
                            .actionUrl(String.format("/project-workspace?milestoneId=%d", projectId, milestoneId))
                            .build());
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho money split: {}", e.getMessage());
        }

        return mapToMoneySplitResponse(saved);
    }

    @Override
    @Transactional
    public MilestoneMoneySplitResponse updateMoneySplit(Long projectId, Long milestoneId, Long moneySplitId,
            UpdateMoneySplitRequest request, Authentication auth) {
        log.info("Cập nhật phân chia tiền: projectId={}, milestoneId={}, moneySplitId={}",
                projectId, milestoneId, moneySplitId);

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Milestone milestone = validateMilestone(projectId, milestoneId);

        MilestoneMoneySplit moneySplit = moneySplitRepository.findById(moneySplitId)
                .orElseThrow(() -> new AppException(ErrorCode.MONEY_SPLIT_NOT_FOUND));

        if (!moneySplit.getMilestone().getId().equals(milestoneId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (moneySplit.getStatus() == MoneySplitStatus.APPROVED) {
            throw new AppException(ErrorCode.MONEY_SPLIT_CANNOT_UPDATE_APPROVED);
        }
        if (moneySplit.getStatus() == MoneySplitStatus.REJECTED) {
            throw new AppException(ErrorCode.MONEY_SPLIT_CANNOT_UPDATE_REJECTED);
        }

        // Kiểm tra milestone đã completed thì không được cập nhật phân chia tiền nữa
        if (milestone.getStatus() == com.fpt.producerworkbench.common.MilestoneStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cột mốc đã hoàn thành, không thể cập nhật phân chia tiền");
        }

        BigDecimal newAmount = new BigDecimal(request.getAmount());
        if (newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        validateTotalAmount(milestone, newAmount, moneySplitId);

        moneySplit.setAmount(newAmount);
        moneySplit.setNote(request.getNote());
        moneySplit.setStatus(MoneySplitStatus.PENDING);
        moneySplit.setRejectionReason(null);

        MilestoneMoneySplit saved = moneySplitRepository.save(moneySplit);

        log.info("Đã cập nhật phân chia tiền: moneySplitId={}, newAmount={}", moneySplitId, newAmount);

        Project project = milestone.getContract().getProject();
        sendMoneySplitNotificationEmail(saved.getUser(), project, milestone, newAmount, request.getNote(), "updated");

        try {
            User currentUser = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            String amountStr = newAmount != null ? newAmount.stripTrailingZeros().toPlainString() : "0";

            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(saved.getUser().getId())
                            .type(NotificationType.MONEY_SPLIT_REQUEST)
                            .title("Phân chia tiền đã được cập nhật")
                            .message(String.format(
                                    "%s đã cập nhật phân chia tiền thành %s cho bạn trong milestone \"%s\" của dự án \"%s\"%s",
                                    currentUser.getFullName() != null ? currentUser.getFullName()
                                            : currentUser.getEmail(),
                                    amountStr,
                                    milestone.getTitle(),
                                    project.getTitle(),
                                    request.getNote() != null && !request.getNote().isBlank()
                                            ? " - " + request.getNote()
                                            : ""))
                            .relatedEntityType(RelatedEntityType.MONEY_SPLIT)
                            .relatedEntityId(saved.getId())
                            .actionUrl(String.format("/project-workspace?milestoneId=%d", projectId, milestoneId))
                            .build());
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho money split update: {}", e.getMessage());
        }

        return mapToMoneySplitResponse(saved);
    }

    @Override
    @Transactional
    public void deleteMoneySplit(Long projectId, Long milestoneId, Long moneySplitId, Authentication auth) {
        log.info("Xóa phân chia tiền: projectId={}, milestoneId={}, moneySplitId={}",
                projectId, milestoneId, moneySplitId);

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        validateMilestone(projectId, milestoneId);

        MilestoneMoneySplit moneySplit = moneySplitRepository.findById(moneySplitId)
                .orElseThrow(() -> new AppException(ErrorCode.MONEY_SPLIT_NOT_FOUND));

        if (!moneySplit.getMilestone().getId().equals(milestoneId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (moneySplit.getStatus() == MoneySplitStatus.APPROVED) {
            throw new AppException(ErrorCode.MONEY_SPLIT_CANNOT_DELETE_APPROVED);
        }

        moneySplitRepository.delete(moneySplit);

        log.info("Đã xóa phân chia tiền: moneySplitId={}", moneySplitId);
    }

    @Override
    @Transactional
    public MilestoneMoneySplitResponse approveMoneySplit(Long projectId, Long milestoneId, Long moneySplitId,
            ApproveRejectMoneySplitRequest request, Authentication auth) {
        log.info("Chấp nhận phân chia tiền: projectId={}, milestoneId={}, moneySplitId={}",
                projectId, milestoneId, moneySplitId);

        Milestone milestone = validateMilestone(projectId, milestoneId);

        User currentUser = getCurrentUser(auth);

        MilestoneMoneySplit moneySplit = moneySplitRepository.findById(moneySplitId)
                .orElseThrow(() -> new AppException(ErrorCode.MONEY_SPLIT_NOT_FOUND));

        if (!moneySplit.getMilestone().getId().equals(milestoneId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (!moneySplit.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.MONEY_SPLIT_ONLY_MEMBER_CAN_APPROVE);
        }

        if (moneySplit.getStatus() == MoneySplitStatus.APPROVED) {
            throw new AppException(ErrorCode.MONEY_SPLIT_ALREADY_APPROVED);
        }
        if (moneySplit.getStatus() == MoneySplitStatus.REJECTED) {
            throw new AppException(ErrorCode.MONEY_SPLIT_ALREADY_REJECTED);
        }

        // Kiểm tra milestone đã completed thì không được chấp nhận phân chia tiền nữa
        if (milestone.getStatus() == com.fpt.producerworkbench.common.MilestoneStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cột mốc đã hoàn thành, không thể chấp nhận phân chia tiền");
        }

        moneySplit.setStatus(MoneySplitStatus.APPROVED);
        moneySplit.setRejectionReason(null);

        MilestoneMoneySplit saved = moneySplitRepository.save(moneySplit);

        log.info("Đã chấp nhận phân chia tiền: moneySplitId={}", moneySplitId);

        Project project = milestone.getContract().getProject();
        User owner = project.getCreator();
        if (owner != null && owner.getEmail() != null) {
            sendMoneySplitApprovalNotificationEmail(owner, project, milestone, saved.getUser(), saved.getAmount(),
                    "approved");
        }

        try {
            if (owner != null) {
                String amountStr = saved.getAmount() != null ? saved.getAmount().stripTrailingZeros().toPlainString()
                        : "0";
                String memberName = saved.getUser().getFullName() != null
                        ? saved.getUser().getFullName()
                        : saved.getUser().getEmail();

                notificationService.sendNotification(
                        SendNotificationRequest.builder()
                                .userId(owner.getId())
                                .type(NotificationType.MONEY_SPLIT_REQUEST)
                                .title("Phân chia tiền đã được chấp nhận")
                                .message(String.format(
                                        "%s đã chấp nhận phân chia tiền %s trong milestone \"%s\" của dự án \"%s\".",
                                        memberName,
                                        amountStr,
                                        milestone.getTitle(),
                                        project.getTitle()))
                                .relatedEntityType(RelatedEntityType.MONEY_SPLIT)
                                .relatedEntityId(saved.getId())
                                .actionUrl(String.format("/project-workspace?milestoneId=%d", milestoneId))
                                .build());
            }
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho owner khi approve money split: {}", e.getMessage());
        }

        return mapToMoneySplitResponse(saved);
    }

    @Override
    @Transactional
    public MilestoneMoneySplitResponse rejectMoneySplit(Long projectId, Long milestoneId, Long moneySplitId,
            ApproveRejectMoneySplitRequest request, Authentication auth) {
        log.info("Từ chối phân chia tiền: projectId={}, milestoneId={}, moneySplitId={}",
                projectId, milestoneId, moneySplitId);

        Milestone milestone = validateMilestone(projectId, milestoneId);

        User currentUser = getCurrentUser(auth);

        MilestoneMoneySplit moneySplit = moneySplitRepository.findById(moneySplitId)
                .orElseThrow(() -> new AppException(ErrorCode.MONEY_SPLIT_NOT_FOUND));

        if (!moneySplit.getMilestone().getId().equals(milestoneId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (!moneySplit.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.MONEY_SPLIT_ONLY_MEMBER_CAN_APPROVE);
        }

        if (moneySplit.getStatus() == MoneySplitStatus.APPROVED) {
            throw new AppException(ErrorCode.MONEY_SPLIT_ALREADY_APPROVED);
        }
        if (moneySplit.getStatus() == MoneySplitStatus.REJECTED) {
            throw new AppException(ErrorCode.MONEY_SPLIT_ALREADY_REJECTED);
        }

        moneySplit.setStatus(MoneySplitStatus.REJECTED);
        moneySplit.setRejectionReason(request.getRejectionReason());

        MilestoneMoneySplit saved = moneySplitRepository.save(moneySplit);

        log.info("Đã từ chối phân chia tiền: moneySplitId={}, reason={}", moneySplitId, request.getRejectionReason());

        Project project = milestone.getContract().getProject();
        User owner = project.getCreator();
        if (owner != null && owner.getEmail() != null) {
            sendMoneySplitApprovalNotificationEmail(owner, project, milestone, saved.getUser(), saved.getAmount(),
                    "rejected");
        }

        try {
            if (owner != null) {
                String amountStr = saved.getAmount() != null ? saved.getAmount().stripTrailingZeros().toPlainString()
                        : "0";
                String memberName = saved.getUser().getFullName() != null
                        ? saved.getUser().getFullName()
                        : saved.getUser().getEmail();
                String reason = request.getRejectionReason() != null && !request.getRejectionReason().isBlank()
                        ? " Lý do: " + request.getRejectionReason()
                        : "";

                notificationService.sendNotification(
                        SendNotificationRequest.builder()
                                .userId(owner.getId())
                                .type(NotificationType.MONEY_SPLIT_REQUEST)
                                .title("Phân chia tiền đã bị từ chối")
                                .message(String.format(
                                        "%s đã từ chối phân chia tiền %s trong milestone \"%s\" của dự án \"%s\".%s",
                                        memberName,
                                        amountStr,
                                        milestone.getTitle(),
                                        project.getTitle(),
                                        reason))
                                .relatedEntityType(RelatedEntityType.MONEY_SPLIT)
                                .relatedEntityId(saved.getId())
                                .actionUrl(String.format("/project-workspace?milestoneId=%d", milestoneId))
                                .build());
            }
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho owner khi reject money split: {}", e.getMessage());
        }

        return mapToMoneySplitResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MilestoneMoneySplitDetailResponse getMoneySplitDetail(Long projectId, Long milestoneId,
            Authentication auth) {
        log.info("Lấy chi tiết phân chia tiền: projectId={}, milestoneId={}", projectId, milestoneId);

        Milestone milestone = validateMilestone(projectId, milestoneId);

        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User currentUser = getCurrentUser(auth);

        Project project = milestone.getContract().getProject();
        Long ownerId = project.getCreator() != null ? project.getCreator().getId() : null;
        Long clientId = project.getClient() != null ? project.getClient().getId() : null;

        boolean isAdmin = currentUser.getRole() == com.fpt.producerworkbench.common.UserRole.ADMIN;
        boolean isOwner = ownerId != null && currentUser.getId().equals(ownerId);
        boolean isClient = clientId != null && currentUser.getId().equals(clientId);
        boolean isMilestoneMember = milestoneMemberRepository.existsByMilestoneIdAndUserId(milestoneId,
                currentUser.getId());

        // Khách hàng không được xem phần phân chia tiền
        if (isClient && !isAdmin) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (!isAdmin && !isOwner && !isMilestoneMember) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Long currentUserId = currentUser.getId();

        List<MilestoneMoneySplit> allMoneySplits = moneySplitRepository.findByMilestoneId(milestoneId);
        List<MilestoneExpense> allExpenses = expenseRepository.findByMilestoneId(milestoneId);

        List<MilestoneMoneySplit> visibleMoneySplits;
        List<MilestoneExpense> visibleExpenses;
        BigDecimal totalSplitAmount;
        BigDecimal totalExpenseAmount;
        BigDecimal totalAllocated;
        BigDecimal remainingAmount;

        if (isAdmin || isOwner) {
            // Admin hoặc Owner xem tất cả
            visibleMoneySplits = allMoneySplits;
            visibleExpenses = allExpenses;

            totalSplitAmount = allMoneySplits.stream()
                    .map(MilestoneMoneySplit::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalExpenseAmount = allExpenses.stream()
                    .map(MilestoneExpense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalAllocated = totalSplitAmount.add(totalExpenseAmount);
            // Tính remaining dựa trên số tiền có thể chia (không trừ thuế vì thuế sẽ được
            // tính khi chuyển tiền vào balance)
            BigDecimal availableAmount = getAvailableAmountForSplit(milestone);
            remainingAmount = availableAmount.subtract(totalAllocated);
        } else {
            visibleMoneySplits = allMoneySplits.stream()
                    .filter(ms -> ms.getUser() != null && ms.getUser().getId().equals(currentUserId))
                    .collect(Collectors.toList());

            visibleExpenses = List.of();

            totalSplitAmount = visibleMoneySplits.stream()
                    .map(MilestoneMoneySplit::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalExpenseAmount = BigDecimal.ZERO;
            totalAllocated = totalSplitAmount;

            remainingAmount = null;
        }

        final Long finalCurrentUserId = currentUserId;
        List<MilestoneMoneySplitResponse> moneySplitResponses = visibleMoneySplits.stream()
                .map(ms -> mapToMoneySplitResponse(ms, finalCurrentUserId))
                .collect(Collectors.toList());

        List<MilestoneExpenseResponse> expenseResponses = visibleExpenses.stream()
                .map(this::mapToExpenseResponse)
                .collect(Collectors.toList());

        BigDecimal milestoneAmount = milestone.getAmount();

        return MilestoneMoneySplitDetailResponse.builder()
                .moneySplits(moneySplitResponses)
                .expenses(expenseResponses)
                .totalSplitAmount(totalSplitAmount)
                .totalExpenseAmount(totalExpenseAmount)
                .totalAllocated(totalAllocated)
                .milestoneAmount(milestoneAmount)
                .remainingAmount(remainingAmount)
                .build();
    }

    @Override
    @Transactional
    public MilestoneExpenseResponse createExpense(Long projectId, Long milestoneId, CreateExpenseRequest request,
            Authentication auth) {
        log.info("Tạo chi phí: projectId={}, milestoneId={}, name={}", projectId, milestoneId, request.getName());

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Milestone milestone = validateMilestone(projectId, milestoneId);

        BigDecimal amount = new BigDecimal(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        validateTotalAmountForExpense(milestone, amount, null);

        MilestoneExpense expense = MilestoneExpense.builder()
                .milestone(milestone)
                .name(request.getName())
                .description(request.getDescription())
                .amount(amount)
                .build();

        MilestoneExpense saved = expenseRepository.save(expense);

        log.info("Đã tạo chi phí: expenseId={}, name={}, amount={}", saved.getId(), request.getName(), amount);

        return mapToExpenseResponse(saved);
    }

    @Override
    @Transactional
    public MilestoneExpenseResponse updateExpense(Long projectId, Long milestoneId, Long expenseId,
            UpdateExpenseRequest request, Authentication auth) {
        log.info("Cập nhật chi phí: projectId={}, milestoneId={}, expenseId={}",
                projectId, milestoneId, expenseId);

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Milestone milestone = validateMilestone(projectId, milestoneId);

        MilestoneExpense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPENSE_NOT_FOUND));

        if (!expense.getMilestone().getId().equals(milestoneId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        BigDecimal newAmount = new BigDecimal(request.getAmount());
        if (newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        validateTotalAmountForExpense(milestone, newAmount, expenseId);

        expense.setName(request.getName());
        expense.setDescription(request.getDescription());
        expense.setAmount(newAmount);

        MilestoneExpense saved = expenseRepository.save(expense);

        log.info("Đã cập nhật chi phí: expenseId={}, newAmount={}", expenseId, newAmount);

        return mapToExpenseResponse(saved);
    }

    @Override
    @Transactional
    public void deleteExpense(Long projectId, Long milestoneId, Long expenseId, Authentication auth) {
        log.info("Xóa chi phí: projectId={}, milestoneId={}, expenseId={}",
                projectId, milestoneId, expenseId);

        var permission = projectPermissionService.checkMilestonePermissions(auth, projectId);
        if (!permission.isOwner()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        validateMilestone(projectId, milestoneId);

        MilestoneExpense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPENSE_NOT_FOUND));

        if (!expense.getMilestone().getId().equals(milestoneId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        expenseRepository.delete(expense);

        log.info("Đã xóa chi phí: expenseId={}", expenseId);
    }

    private Milestone validateMilestone(Long projectId, Long milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));

        if (milestone.getContract() == null || milestone.getContract().getProject() == null
                || !milestone.getContract().getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Contract contract = milestone.getContract();
        if (!ContractStatus.PAID.equals(contract.getSignnowStatus())
                && !ContractStatus.COMPLETED.equals(contract.getSignnowStatus())) {
            throw new AppException(ErrorCode.CONTRACT_NOT_COMPLETED_FOR_MILESTONE);
        }

        return milestone;
    }

    /**
     * Tính số tiền có thể phân chia cho các thành viên.
     * Số tiền có thể chia = amount (không trừ thuế vì thuế sẽ được tính khi chuyển
     * tiền vào balance)
     * 
     * @param milestone Milestone cần tính
     * @return Số tiền có thể chia (amount)
     */
    private BigDecimal getAvailableAmountForSplit(Milestone milestone) {
        BigDecimal amount = milestone.getAmount() != null ? milestone.getAmount() : BigDecimal.ZERO;
        return amount;
    }

    private void validateTotalAmount(Milestone milestone, BigDecimal newAmount, Long excludeMoneySplitId) {
        List<MilestoneMoneySplit> moneySplits = moneySplitRepository.findByMilestoneId(milestone.getId());
        List<MilestoneExpense> expenses = expenseRepository.findByMilestoneId(milestone.getId());

        BigDecimal totalSplitAmount = moneySplits.stream()
                .filter(ms -> excludeMoneySplitId == null || !ms.getId().equals(excludeMoneySplitId))
                .map(MilestoneMoneySplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpenseAmount = expenses.stream()
                .map(MilestoneExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAllocated = totalSplitAmount.add(totalExpenseAmount).add(newAmount);
        // Sử dụng số tiền có thể chia (không trừ thuế vì thuế sẽ được tính khi chuyển
        // tiền vào balance)
        BigDecimal availableAmount = getAvailableAmountForSplit(milestone);

        if (totalAllocated.compareTo(availableAmount) > 0) {
            throw new AppException(ErrorCode.MONEY_SPLIT_TOTAL_EXCEEDS_MILESTONE);
        }
    }

    private void validateTotalAmountForExpense(Milestone milestone, BigDecimal newAmount, Long excludeExpenseId) {
        List<MilestoneMoneySplit> moneySplits = moneySplitRepository.findByMilestoneId(milestone.getId());
        List<MilestoneExpense> expenses = expenseRepository.findByMilestoneId(milestone.getId());

        BigDecimal totalSplitAmount = moneySplits.stream()
                .map(MilestoneMoneySplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpenseAmount = expenses.stream()
                .filter(e -> excludeExpenseId == null || !e.getId().equals(excludeExpenseId))
                .map(MilestoneExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAllocated = totalSplitAmount.add(totalExpenseAmount).add(newAmount);
        // Sử dụng số tiền có thể chia (không trừ thuế vì thuế sẽ được tính khi chuyển
        // tiền vào balance)
        BigDecimal availableAmount = getAvailableAmountForSplit(milestone);

        if (totalAllocated.compareTo(availableAmount) > 0) {
            throw new AppException(ErrorCode.MONEY_SPLIT_TOTAL_EXCEEDS_MILESTONE);
        }
    }

    private User getCurrentUser(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private MilestoneMoneySplitResponse mapToMoneySplitResponse(MilestoneMoneySplit moneySplit) {
        return mapToMoneySplitResponse(moneySplit, null);
    }

    private MilestoneMoneySplitResponse mapToMoneySplitResponse(MilestoneMoneySplit moneySplit, Long currentUserId) {
        LocalDateTime createdAt = moneySplit.getCreatedAt() != null
                ? moneySplit.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null;
        LocalDateTime updatedAt = moneySplit.getUpdatedAt() != null
                ? moneySplit.getUpdatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null;

        Long moneySplitUserId = moneySplit.getUser() != null ? moneySplit.getUser().getId() : null;
        String moneySplitUserEmail = moneySplit.getUser() != null ? moneySplit.getUser().getEmail() : null;

        Boolean isCurrentUserRecipient = null;
        if (currentUserId != null && moneySplitUserId != null) {
            isCurrentUserRecipient = currentUserId.equals(moneySplitUserId);
            log.debug("MoneySplit ID: {}, moneySplitUserId: {}, currentUserId: {}, isCurrentUserRecipient: {}",
                    moneySplit.getId(), moneySplitUserId, currentUserId, isCurrentUserRecipient);
        } else {
            log.debug(
                    "MoneySplit ID: {}, moneySplitUserId: {}, currentUserId: {}, isCurrentUserRecipient: null (không có currentUserId hoặc moneySplitUserId)",
                    moneySplit.getId(), moneySplitUserId, currentUserId);
        }

        return MilestoneMoneySplitResponse.builder()
                .id(moneySplit.getId())
                .userId(moneySplitUserId)
                .userName(moneySplit.getUser() != null ? moneySplit.getUser().getFullName() : null)
                .userEmail(moneySplitUserEmail)
                .amount(moneySplit.getAmount())
                .status(moneySplit.getStatus())
                .note(moneySplit.getNote())
                .rejectionReason(moneySplit.getRejectionReason())
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .isCurrentUserRecipient(isCurrentUserRecipient)
                .build();
    }

    private MilestoneExpenseResponse mapToExpenseResponse(MilestoneExpense expense) {
        LocalDateTime createdAt = expense.getCreatedAt() != null
                ? expense.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null;
        LocalDateTime updatedAt = expense.getUpdatedAt() != null
                ? expense.getUpdatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null;

        return MilestoneExpenseResponse.builder()
                .id(expense.getId())
                .name(expense.getName())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    private void sendMoneySplitNotificationEmail(User user, Project project, Milestone milestone,
            BigDecimal amount, String note, String action) {
        try {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                log.warn("Không thể gửi email thông báo: user {} không có email", user.getId());
                return;
            }

            String projectUrl = String.format("%s/project-workspace?projectId=%d&milestoneId=%d",
                    frontendProperties.getUrl(),
                    project.getId(), milestone.getId());

            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", user.getFullName() != null ? user.getFullName() : user.getEmail());
            params.put("projectName", project.getTitle());
            params.put("milestoneTitle", milestone.getTitle());
            params.put("amount", amount.toString());
            params.put("projectUrl", projectUrl);

            if (note != null && !note.trim().isEmpty()) {
                params.put("note", note);
            }

            String subject = action.equals("created")
                    ? "Bạn đã được phân chia tiền trong Cột mốc: " + milestone.getTitle()
                    : "Phân chia tiền đã được cập nhật trong Cột mốc: " + milestone.getTitle();

            String templateCode = action.equals("created")
                    ? "milestone-money-split-created-template"
                    : "milestone-money-split-updated-template";

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(user.getEmail())
                    .subject(subject)
                    .templateCode(templateCode)
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo phân chia tiền qua Kafka: userId={}, milestoneId={}, action={}",
                    user.getId(), milestone.getId(), action);

        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo phân chia tiền qua Kafka: userId={}, milestoneId={}",
                    user.getId(), milestone.getId(), e);
        }
    }

    private void sendMoneySplitApprovalNotificationEmail(User owner, Project project, Milestone milestone,
            User member, BigDecimal amount, String action) {
        try {
            if (owner.getEmail() == null || owner.getEmail().isBlank()) {
                log.warn("Không thể gửi email thông báo: owner {} không có email", owner.getId());
                return;
            }

            String projectUrl = String.format("%s/project-workspace?projectId=%d&milestoneId=%d",
                    frontendProperties.getUrl(),
                    project.getId(), milestone.getId());

            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", owner.getFullName() != null ? owner.getFullName() : owner.getEmail());
            params.put("projectName", project.getTitle());
            params.put("milestoneTitle", milestone.getTitle());
            params.put("memberName", member.getFullName() != null ? member.getFullName() : member.getEmail());
            params.put("amount", amount.toString());
            params.put("projectUrl", projectUrl);

            String subject = action.equals("approved")
                    ? "Phân chia tiền đã được chấp nhận trong Cột mốc: " + milestone.getTitle()
                    : "Phân chia tiền đã bị từ chối trong Cột mốc: " + milestone.getTitle();

            String templateCode = action.equals("approved")
                    ? "milestone-money-split-approved-template"
                    : "milestone-money-split-rejected-template";

            NotificationEvent event = NotificationEvent.builder()
                    .recipient(owner.getEmail())
                    .subject(subject)
                    .templateCode(templateCode)
                    .param(params)
                    .build();

            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo phê duyệt phân chia tiền qua Kafka: ownerId={}, milestoneId={}, action={}",
                    owner.getId(), milestone.getId(), action);

        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo phê duyệt phân chia tiền qua Kafka: ownerId={}, milestoneId={}",
                    owner.getId(), milestone.getId(), e);
        }
    }
}
