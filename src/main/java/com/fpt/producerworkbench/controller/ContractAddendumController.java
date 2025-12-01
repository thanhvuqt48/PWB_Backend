package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractAddendumPdfFillRequest;
import com.fpt.producerworkbench.dto.request.ContractInviteRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.StartSigningResponse;
import com.fpt.producerworkbench.service.ContractAddendumInviteService;
import com.fpt.producerworkbench.service.ContractAddendumPdfService;
import com.fpt.producerworkbench.service.ContractAddendumService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/addendum")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ContractAddendumController {

    ContractAddendumPdfService pdfService;
    ContractAddendumInviteService inviteService;
    ContractAddendumService addendumService;

    @PostMapping(value = "/pdf/fill", produces = "application/pdf")
    public ResponseEntity<byte[]> fillPdf(
            Authentication auth,
            @PathVariable Long contractId,
            @Valid @RequestBody ContractAddendumPdfFillRequest req,
            HttpServletRequest http
    ) {
        byte[] pdf = pdfService.fillAddendum(auth, contractId, req);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=addendum.pdf")
                .body(pdf);
    }

    @PostMapping("/invites")
    public ApiResponse<StartSigningResponse> invite(
            @PathVariable Long contractId,
            @RequestBody(required = false) ContractInviteRequest req,
            Authentication auth
    ) {
        if (req == null) req = new ContractInviteRequest();
        var result = inviteService.inviteAddendum(auth, contractId, req);
        return ApiResponse.<StartSigningResponse>builder().code(200).result(result).build();
    }

    /**
     * Lấy tất cả phụ lục hợp đồng của một contract.
     * Chỉ trả về phiên bản cuối cùng của mỗi phụ lục (theo addendumNumber).
     */
    @GetMapping("/all")
    public ApiResponse<List<Map<String, Object>>> getAllAddendumsByContract(@PathVariable Long contractId) {
        List<Map<String, Object>> result = addendumService.getAllAddendumsByContract(contractId);
        return ApiResponse.<List<Map<String, Object>>>builder().code(200).result(result).build();
    }

    /**
     * Lấy thông tin phụ lục hợp đồng mới nhất.
     * Trả về trạng thái phụ lục, version, link tải file PDF (ADDENDUM hoặc SIGNED).
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> getAddendumByContract(@PathVariable Long contractId) {
        Map<String, Object> result = addendumService.getAddendumByContract(contractId);
        return ApiResponse.<Map<String, Object>>builder().code(200).result(result).build();
    }

    /**
     * Xem file PDF phụ lục hợp đồng đã điền (bản ADDENDUM mới nhất).
     * Redirect về presigned URL trong storage. Yêu cầu quyền xem hợp đồng.
     */
    @GetMapping("/file")
    public ResponseEntity<Void> viewAddendumFile(
            @PathVariable Long contractId,
            Authentication auth
    ) {
        String url = addendumService.getAddendumFileUrl(contractId, auth);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }

    /**
     * Xem file PDF phụ lục hợp đồng đã ký (signed).
     * Redirect đến presigned URL của file đã ký.
     */
    @GetMapping("/signed/file")
    public ResponseEntity<Void> viewSignedAddendumFile(@PathVariable Long contractId) {
        String url = addendumService.getSignedAddendumFileUrl(contractId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }

    /**
     * Từ chối phụ lục hợp đồng với lý do cụ thể.
     * Chỉ Admin hoặc Client của project mới có thể từ chối. Tự động gửi thông báo email cho owner.
     */
    @PostMapping("/decline")
    public ApiResponse<String> decline(
            @PathVariable Long contractId,
            @RequestBody String reason,
            Authentication auth
    ) throws Exception {
        String result = addendumService.declineAddendum(contractId, reason, auth);
        return ApiResponse.<String>builder().code(200).result(result).build();
    }

    /**
     * Lấy lý do từ chối phụ lục hợp đồng.
     * Chỉ Admin, Owner hoặc Client của project mới có thể xem. Chỉ áp dụng khi phụ lục đã bị từ chối.
     */
    @GetMapping("/decline-reason")
    public ApiResponse<String> getDeclineReason(@PathVariable Long contractId, Authentication auth) {
        String reason = addendumService.getDeclineReason(contractId, auth);
        return ApiResponse.<String>builder().code(200).result(reason).build();
    }
}
