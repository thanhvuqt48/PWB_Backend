package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.MilestoneSummaryResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.MilestoneService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ContractMilestoneController {

    MilestoneService milestoneService;

    /**
     * Lấy danh sách milestone của 1 contract
     * Chỉ trả về summary: description, amount, (có thể thêm id, sequence nếu cần)
     */
    @GetMapping("/{contractId}/milestones/summary")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<List<MilestoneSummaryResponse>> getMilestoneSummaries(
            Authentication auth,
            @PathVariable Long contractId
    ) {
        if (contractId == null || contractId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        var result = milestoneService.getMilestoneSummariesByContractId(auth, contractId);

        return ApiResponse.<List<MilestoneSummaryResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách cột mốc theo hợp đồng thành công")
                .result(result)
                .build();
    }
}
