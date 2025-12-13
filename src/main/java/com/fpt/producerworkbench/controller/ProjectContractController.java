package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.PartyBInfoResponse;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.service.ProjectContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;

/**
 * Controller quản lý các thao tác liên quan đến hợp đồng của project.
 * Bao gồm: xem thông tin hợp đồng, mời ký hợp đồng, từ chối hợp đồng, xem file PDF, và điền thông tin vào template PDF.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ProjectContractController {

    private final ProjectContractService projectContractService;

    /**
     * Lấy thông tin hợp đồng của project.
     * Trả về trạng thái hợp đồng, link tải file PDF (filled hoặc signed), và thông tin tài trợ.
     */
    @GetMapping("/projects/{projectId}/contract")
    public ApiResponse<Map<String, Object>> getContractByProject(@PathVariable Long projectId) {
        Map<String, Object> result = projectContractService.getContractByProject(projectId);
        return ApiResponse.<Map<String, Object>>builder().code(200).result(result).build();
    }

    /**
     * Mời các bên ký hợp đồng thông qua SignNow.
     * Hỗ trợ gửi email mời ký hoặc embedded signing. Request body có thể null để sử dụng cấu hình mặc định.
     */
    @PostMapping("/contracts/{id}/invites")
    public ApiResponse<StartSigningResponse> invite(@PathVariable Long id,
                                                    @RequestBody(required = false) ContractInviteRequest req,
                                                    Authentication auth) {
        if (req == null) {
            req = new ContractInviteRequest();
        }
        StartSigningResponse result = projectContractService.invite(auth, id, req);
        return ApiResponse.<StartSigningResponse>builder().code(200).result(result).build();
    }

    /**
     * Từ chối hợp đồng với lý do cụ thể.
     * Chỉ Admin hoặc Client của project mới có thể từ chối. Tự động gửi thông báo email cho owner.
     */
    @PostMapping("/contracts/{id}/decline")
    public ApiResponse<String> decline(@PathVariable Long id,
                                       @RequestBody String reason,
                                       Authentication auth) throws Exception {
        String result = projectContractService.decline(auth, id, reason);
        return ApiResponse.<String>builder().code(200).result(result).build();
    }

    /**
     * Lấy lý do từ chối hợp đồng.
     * Chỉ Admin, Owner hoặc Client của project mới có thể xem. Chỉ áp dụng khi hợp đồng đã bị từ chối.
     */
    @GetMapping("/contracts/{id}/decline-reason")
    public ApiResponse<String> getDeclineReason(@PathVariable Long id, Authentication auth) {
        String result = projectContractService.getDeclineReason(auth, id);
        return ApiResponse.<String>builder()
                .code(200)
                .result(result)
                .build();
    }

    /**
     * Xem file PDF hợp đồng đã điền (filled).
     * Redirect đến presigned URL của file. Yêu cầu quyền xem hợp đồng.
     */
    @GetMapping("/contracts/{id}/filled/file")
    public ResponseEntity<Void> redirectToFilled(@PathVariable("id") Long id, Authentication auth) {
        return projectContractService.redirectToFilled(id, auth);
    }

    /**
     * Xem file PDF hợp đồng đã ký (signed).
     * Redirect đến presigned URL của file đã ký.
     */
    @GetMapping("/contracts/{id}/signed/file")
    public ResponseEntity<Void> viewSignedPdf(@PathVariable Long id) {
        return projectContractService.viewSignedPdf(id);
    }

    /**
     * Điền thông tin vào template PDF hợp đồng và trả về file PDF đã điền.
     * Trả về file PDF binary với Content-Type: application/pdf.
     */
    @PostMapping(value = "/contracts/pdf/{projectId}/fill", produces = "application/pdf")
    public ResponseEntity<byte[]> fill(
            Authentication auth,
            @PathVariable Long projectId,
            @Valid @RequestBody ContractPdfFillRequest req,
            HttpServletRequest request
    ) {
        byte[] pdf = projectContractService.fillContractPdf(auth, projectId, req);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=contract.pdf")
                .body(pdf);
    }

    /**
     * Lấy thông tin Bên B (Nhà Sản xuất) từ tài khoản đã xác thực CCCD.
     * Dùng để tự động fill vào form khi user bấm nút "Lấy từ xác thực".
     */
    @GetMapping("/contracts/party-b/verified-info")
    public ApiResponse<PartyBInfoResponse> getVerifiedPartyBInfo(Authentication auth) {
        PartyBInfoResponse result = projectContractService.getVerifiedPartyBInfo(auth);
        return ApiResponse.<PartyBInfoResponse>builder()
                .code(200)
                .message("Lấy thông tin Bên B từ xác thực thành công")
                .result(result)
                .build();
    }
}
