package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.*;
import org.springframework.security.core.Authentication;

/**
 * Service xử lý chấm dứt hợp đồng
 */
public interface ContractTerminationService {
    
    /**
     * Preview tính toán trước khi chấm dứt (không thực hiện)
     * Không cần lý do, chỉ xem trước tính toán
     * Trả về ClientTerminationPreviewResponse hoặc OwnerTerminationPreviewResponse
     * tùy thuộc vào user đăng nhập
     */
    Object previewTermination(
        Long contractId,
        Authentication auth
    );
    
    /**
     * Thực hiện chấm dứt hợp đồng
     */
    TerminationResponse terminateContract(
        Long contractId,
        TerminationRequest request,
        Authentication auth
    );
    
    /**
     * Lấy chi tiết chấm dứt hợp đồng
     */
    TerminationDetailResponse getTerminationDetail(
        Long contractId,
        Authentication auth
    );
    
    /**
     * Xử lý webhook PayOS khi Owner đã chuyển tiền đền bù Team
     */
    void handleOwnerCompensationWebhook(String orderCode, String status);
}


