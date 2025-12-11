package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.response.MilestoneExpenseResponse;
import com.fpt.producerworkbench.dto.response.MilestoneMoneySplitResponse;
import com.fpt.producerworkbench.dto.response.ProjectExpenseChartResponse;
import com.fpt.producerworkbench.dto.response.ProjectExpenseDetailResponse;
import com.fpt.producerworkbench.dto.response.ProjectMoneySplitDetailResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.ProjectExpenseService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectExpenseServiceImpl implements ProjectExpenseService {

    private final ProjectRepository projectRepository;
    private final ContractRepository contractRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneExpenseRepository expenseRepository;
    private final MilestoneMoneySplitRepository moneySplitRepository;
    private final UserRepository userRepository;
    private final ProjectPermissionService projectPermissionService;

    @Override
    @Transactional(readOnly = true)
    public ProjectExpenseChartResponse getProjectExpenseChart(Long projectId, Authentication auth) {
        log.info("Lấy thống kê chi phí dự án: projectId={}", projectId);

        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        var permission = projectPermissionService.checkProjectPermissions(auth, projectId);
        if (permission.getProject() == null || !permission.getProject().isCanViewProject()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        boolean isCustomer = project.getClient() != null &&
                currentUser.getId().equals(project.getClient().getId());
        
        Contract contract = contractRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        BigDecimal contractTotalAmount = contract.getTotalAmount();

        if (isCustomer) {
            return ProjectExpenseChartResponse.builder()
                    .contractTotalAmount(contractTotalAmount)
                    .totalExpenseAmount(BigDecimal.ZERO)
                    .totalMoneySplitAmount(BigDecimal.ZERO)
                    .remainingAmount(BigDecimal.ZERO)
                    .remainingAfterTax(BigDecimal.ZERO)
                    .totalTax(BigDecimal.ZERO)
                    .percentages(new HashMap<>())
                    .build();
        }

        List<Milestone> milestones = milestoneRepository.findByContractIdOrderBySequenceAsc(contract.getId());

        BigDecimal totalExpenseAmount = BigDecimal.ZERO;
        BigDecimal totalMoneySplitAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        for (Milestone milestone : milestones) {
            List<MilestoneExpense> expenses = expenseRepository.findByMilestoneId(milestone.getId());
            BigDecimal milestoneExpenseTotal = expenses.stream()
                    .map(MilestoneExpense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalExpenseAmount = totalExpenseAmount.add(milestoneExpenseTotal);

            List<MilestoneMoneySplit> moneySplits = moneySplitRepository.findByMilestoneId(milestone.getId());
            BigDecimal milestoneMoneySplitTotal = moneySplits.stream()
                    .map(MilestoneMoneySplit::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalMoneySplitAmount = totalMoneySplitAmount.add(milestoneMoneySplitTotal);

            BigDecimal milestonePitTax = milestone.getPitTax() != null ? milestone.getPitTax() : BigDecimal.ZERO;
            BigDecimal milestoneVatTax = milestone.getVatTax() != null ? milestone.getVatTax() : BigDecimal.ZERO;
            totalTax = totalTax.add(milestonePitTax).add(milestoneVatTax);
        }

        BigDecimal totalAllocated = totalExpenseAmount.add(totalMoneySplitAmount);
        BigDecimal remainingAmount = contractTotalAmount.subtract(totalAllocated);

        BigDecimal remainingAfterTax = remainingAmount.subtract(totalTax);
        if (remainingAfterTax.compareTo(BigDecimal.ZERO) < 0) {
            remainingAfterTax = BigDecimal.ZERO;
        }

        Map<String, BigDecimal> percentages = calculatePercentages(
                contractTotalAmount,
                totalExpenseAmount,
                totalMoneySplitAmount,
                remainingAfterTax,
                totalTax
        );

        return ProjectExpenseChartResponse.builder()
                .totalExpenseAmount(totalExpenseAmount)
                .totalMoneySplitAmount(totalMoneySplitAmount)
                .remainingAmount(remainingAmount)
                .remainingAfterTax(remainingAfterTax)
                .totalTax(totalTax)
                .contractTotalAmount(contractTotalAmount)
                .percentages(percentages)
                .build();
    }

    private Map<String, BigDecimal> calculatePercentages(
            BigDecimal contractTotal,
            BigDecimal totalExpense,
            BigDecimal totalMoneySplit,
            BigDecimal remainingAfterTax,
            BigDecimal totalTax) {
        
        Map<String, BigDecimal> percentages = new HashMap<>();
        
        if (contractTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal expensePercent = totalExpense
                    .divide(contractTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal moneySplitPercent = totalMoneySplit
                    .divide(contractTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal taxPercent = totalTax
                    .divide(contractTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal remainingPercent = remainingAfterTax
                    .divide(contractTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            
            percentages.put("expense", expensePercent);
            percentages.put("moneySplit", moneySplitPercent);
            percentages.put("tax", taxPercent);
            percentages.put("remaining", remainingPercent);
        } else {
            percentages.put("expense", BigDecimal.ZERO);
            percentages.put("moneySplit", BigDecimal.ZERO);
            percentages.put("tax", BigDecimal.ZERO);
            percentages.put("remaining", BigDecimal.ZERO);
        }
        
        return percentages;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectExpenseDetailResponse> getProjectExpenseDetails(Long projectId, Authentication auth) {
        log.info("Lấy chi tiết chi phí dịch vụ theo milestone: projectId={}", projectId);

        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        var permission = projectPermissionService.checkProjectPermissions(auth, projectId);
        if (permission.getProject() == null || !permission.getProject().isCanViewProject()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        boolean isCustomer = project.getClient() != null &&
                currentUser.getId().equals(project.getClient().getId());
        if (isCustomer) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Contract contract = contractRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        List<Milestone> milestones = milestoneRepository.findByContractIdOrderBySequenceAsc(contract.getId());

        return milestones.stream()
                .map(milestone -> {
                    List<MilestoneExpense> expenses = expenseRepository.findByMilestoneId(milestone.getId());
                    BigDecimal totalExpenseAmount = expenses.stream()
                            .map(MilestoneExpense::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    List<MilestoneExpenseResponse> expenseResponses = expenses.stream()
                            .map(this::mapToExpenseResponse)
                            .collect(Collectors.toList());

                    return ProjectExpenseDetailResponse.builder()
                            .milestoneId(milestone.getId())
                            .milestoneTitle(milestone.getTitle())
                            .milestoneSequence(milestone.getSequence())
                            .milestoneTotalAmount(milestone.getAmount())
                            .totalExpenseAmount(totalExpenseAmount)
                            .expenses(expenseResponses)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMoneySplitDetailResponse> getProjectMoneySplitDetails(Long projectId, Authentication auth) {
        log.info("Lấy chi tiết chi phí chia tiền theo milestone: projectId={}", projectId);

        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        var permission = projectPermissionService.checkProjectPermissions(auth, projectId);
        if (permission.getProject() == null || !permission.getProject().isCanViewProject()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        boolean isCustomer = project.getClient() != null &&
                currentUser.getId().equals(project.getClient().getId());
        if (isCustomer) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Contract contract = contractRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        List<Milestone> milestones = milestoneRepository.findByContractIdOrderBySequenceAsc(contract.getId());
        Long currentUserId = currentUser.getId();

        return milestones.stream()
                .map(milestone -> {
                    List<MilestoneMoneySplit> moneySplits = moneySplitRepository.findByMilestoneId(milestone.getId());
                    BigDecimal totalMoneySplitAmount = moneySplits.stream()
                            .map(MilestoneMoneySplit::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    List<MilestoneMoneySplitResponse> moneySplitResponses = moneySplits.stream()
                            .map(ms -> mapToMoneySplitResponse(ms, currentUserId))
                            .collect(Collectors.toList());

                    return ProjectMoneySplitDetailResponse.builder()
                            .milestoneId(milestone.getId())
                            .milestoneTitle(milestone.getTitle())
                            .milestoneSequence(milestone.getSequence())
                            .milestoneTotalAmount(milestone.getAmount())
                            .totalMoneySplitAmount(totalMoneySplitAmount)
                            .moneySplits(moneySplitResponses)
                            .build();
                })
                .collect(Collectors.toList());
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
}

