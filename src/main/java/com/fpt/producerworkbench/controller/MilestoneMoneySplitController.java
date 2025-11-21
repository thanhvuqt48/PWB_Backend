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

/**
 * Controller quản lý phân chia tiền và chi phí của milestone.
 * Bao gồm: tạo, cập nhật, xóa phân chia tiền, chấp nhận/từ chối phân chia tiền,
 * và quản lý chi phí (expense) của milestone.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class MilestoneMoneySplitController {

    private final MilestoneMoneySplitService moneySplitService;

    /**
     * Tạo phân chia tiền cho thành viên trong milestone.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (chỉ Owner). Chỉ có thể phân chia cho thành viên milestone (không phải Owner hoặc Client).
     */
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

    /**
     * Cập nhật phân chia tiền.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (chỉ Owner). Chỉ có thể cập nhật khi status là PENDING.
     */
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

    /**
     * Xóa phân chia tiền.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (chỉ Owner).
     */
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

    /**
     * Chấp nhận phân chia tiền.
     * Người nhận tiền (recipient) có thể chấp nhận phân chia tiền được đề xuất cho họ.
     */
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

    /**
     * Từ chối phân chia tiền.
     * Người nhận tiền (recipient) có thể từ chối phân chia tiền và cung cấp lý do từ chối.
     */
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

    /**
     * Lấy chi tiết phân chia tiền của milestone.
     * Trả về tổng quan về các phân chia tiền và chi phí trong milestone.
     */
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

    /**
     * Tạo chi phí cho milestone.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (chỉ Owner). Chi phí được trừ vào số tiền có thể phân chia.
     */
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

    /**
     * Cập nhật chi phí.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (chỉ Owner).
     */
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

    /**
     * Xóa chi phí.
     * Yêu cầu đăng nhập và có quyền quản lý milestone (chỉ Owner).
     */
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

