package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.*;
import com.fpt.producerworkbench.dto.response.MilestoneMoneySplitDetailResponse;
import com.fpt.producerworkbench.dto.response.MilestoneMoneySplitResponse;
import com.fpt.producerworkbench.dto.response.MilestoneExpenseResponse;
import org.springframework.security.core.Authentication;

public interface MilestoneMoneySplitService {

    MilestoneMoneySplitResponse createMoneySplit(Long projectId, Long milestoneId, CreateMoneySplitRequest request, Authentication auth);

    MilestoneMoneySplitResponse updateMoneySplit(Long projectId, Long milestoneId, Long moneySplitId, UpdateMoneySplitRequest request, Authentication auth);

    void deleteMoneySplit(Long projectId, Long milestoneId, Long moneySplitId, Authentication auth);

    MilestoneMoneySplitResponse approveMoneySplit(Long projectId, Long milestoneId, Long moneySplitId, ApproveRejectMoneySplitRequest request, Authentication auth);

    MilestoneMoneySplitResponse rejectMoneySplit(Long projectId, Long milestoneId, Long moneySplitId, ApproveRejectMoneySplitRequest request, Authentication auth);

    MilestoneMoneySplitDetailResponse getMoneySplitDetail(Long projectId, Long milestoneId, Authentication auth);

    MilestoneExpenseResponse createExpense(Long projectId, Long milestoneId, CreateExpenseRequest request, Authentication auth);

    MilestoneExpenseResponse updateExpense(Long projectId, Long milestoneId, Long expenseId, UpdateExpenseRequest request, Authentication auth);

    void deleteExpense(Long projectId, Long milestoneId, Long expenseId, Authentication auth);
}

