package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.*;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.MilestoneExpenseResponse;
import com.fpt.producerworkbench.dto.response.MilestoneMoneySplitDetailResponse;
import com.fpt.producerworkbench.dto.response.MilestoneMoneySplitResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.MilestoneMoneySplitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class MilestoneMoneySplitController {

    private final MilestoneMoneySplitService moneySplitService;

    @PostMapping("/{projectId}/milestones/{milestoneId}/money-splits")
    public ApiResponse<MilestoneMoneySplitResponse> createMoneySplit(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody CreateMoneySplitRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneMoneySplitResponse response = moneySplitService.createMoneySplit(
                projectId, milestoneId, request, authentication);

        return ApiResponse.<MilestoneMoneySplitResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Tạo phân chia tiền thành công")
                .result(response)
                .build();
    }

    @PutMapping("/{projectId}/milestones/{milestoneId}/money-splits/{moneySplitId}")
    public ApiResponse<MilestoneMoneySplitResponse> updateMoneySplit(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @PathVariable Long moneySplitId,
            @Valid @RequestBody UpdateMoneySplitRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0 
                || moneySplitId == null || moneySplitId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneMoneySplitResponse response = moneySplitService.updateMoneySplit(
                projectId, milestoneId, moneySplitId, request, authentication);

        return ApiResponse.<MilestoneMoneySplitResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Cập nhật phân chia tiền thành công")
                .result(response)
                .build();
    }

    @DeleteMapping("/{projectId}/milestones/{milestoneId}/money-splits/{moneySplitId}")
    public ApiResponse<Void> deleteMoneySplit(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @PathVariable Long moneySplitId,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0 
                || moneySplitId == null || moneySplitId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        moneySplitService.deleteMoneySplit(projectId, milestoneId, moneySplitId, authentication);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Xóa phân chia tiền thành công")
                .build();
    }

    @PostMapping("/{projectId}/milestones/{milestoneId}/money-splits/{moneySplitId}/approve")
    public ApiResponse<MilestoneMoneySplitResponse> approveMoneySplit(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @PathVariable Long moneySplitId,
            @RequestBody ApproveRejectMoneySplitRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0 
                || moneySplitId == null || moneySplitId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneMoneySplitResponse response = moneySplitService.approveMoneySplit(
                projectId, milestoneId, moneySplitId, request, authentication);

        return ApiResponse.<MilestoneMoneySplitResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Chấp nhận phân chia tiền thành công")
                .result(response)
                .build();
    }

    @PostMapping("/{projectId}/milestones/{milestoneId}/money-splits/{moneySplitId}/reject")
    public ApiResponse<MilestoneMoneySplitResponse> rejectMoneySplit(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @PathVariable Long moneySplitId,
            @RequestBody ApproveRejectMoneySplitRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0 
                || moneySplitId == null || moneySplitId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneMoneySplitResponse response = moneySplitService.rejectMoneySplit(
                projectId, milestoneId, moneySplitId, request, authentication);

        return ApiResponse.<MilestoneMoneySplitResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Từ chối phân chia tiền thành công")
                .result(response)
                .build();
    }

    @GetMapping("/{projectId}/milestones/{milestoneId}/money-splits")
    public ApiResponse<MilestoneMoneySplitDetailResponse> getMoneySplitDetail(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneMoneySplitDetailResponse response = moneySplitService.getMoneySplitDetail(
                projectId, milestoneId, authentication);

        return ApiResponse.<MilestoneMoneySplitDetailResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy chi tiết phân chia tiền thành công")
                .result(response)
                .build();
    }

    @PostMapping("/{projectId}/milestones/{milestoneId}/expenses")
    public ApiResponse<MilestoneExpenseResponse> createExpense(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody CreateExpenseRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneExpenseResponse response = moneySplitService.createExpense(
                projectId, milestoneId, request, authentication);

        return ApiResponse.<MilestoneExpenseResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Tạo chi phí thành công")
                .result(response)
                .build();
    }

    @PutMapping("/{projectId}/milestones/{milestoneId}/expenses/{expenseId}")
    public ApiResponse<MilestoneExpenseResponse> updateExpense(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @PathVariable Long expenseId,
            @Valid @RequestBody UpdateExpenseRequest request,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0 
                || expenseId == null || expenseId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        MilestoneExpenseResponse response = moneySplitService.updateExpense(
                projectId, milestoneId, expenseId, request, authentication);

        return ApiResponse.<MilestoneExpenseResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Cập nhật chi phí thành công")
                .result(response)
                .build();
    }

    @DeleteMapping("/{projectId}/milestones/{milestoneId}/expenses/{expenseId}")
    public ApiResponse<Void> deleteExpense(
            @PathVariable Long projectId,
            @PathVariable Long milestoneId,
            @PathVariable Long expenseId,
            Authentication authentication) {
        if (projectId == null || projectId <= 0 || milestoneId == null || milestoneId <= 0 
                || expenseId == null || expenseId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        moneySplitService.deleteExpense(projectId, milestoneId, expenseId, authentication);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Xóa chi phí thành công")
                .build();
    }
}

