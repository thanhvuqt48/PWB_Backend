package com.fpt.producerworkbench.service;

import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

public interface ContractAddendumService {
    
    /**
     * Lấy tất cả phụ lục hợp đồng của một contract.
     * Chỉ trả về phiên bản cuối cùng của mỗi phụ lục (theo addendumNumber).
     */
    List<Map<String, Object>> getAllAddendumsByContract(Long contractId);
    
    /**
     * Lấy thông tin phụ lục hợp đồng mới nhất.
     * Trả về trạng thái phụ lục, version, link tải file PDF (ADDENDUM hoặc SIGNED).
     */
    Map<String, Object> getAddendumByContract(Long contractId);
    
    /**
     * Lấy URL của file PDF phụ lục hợp đồng đã điền (bản ADDENDUM mới nhất).
     * Yêu cầu quyền xem hợp đồng.
     */
    String getAddendumFileUrl(Long contractId, Authentication auth);
    
    /**
     * Lấy URL của file PDF phụ lục hợp đồng đã ký (signed).
     */
    String getSignedAddendumFileUrl(Long contractId);
    
    /**
     * Từ chối phụ lục hợp đồng với lý do cụ thể.
     * Chỉ Admin hoặc Client của project mới có thể từ chối. Tự động gửi thông báo email cho owner.
     */
    String declineAddendum(Long contractId, String reason, Authentication auth) throws Exception;
    
    /**
     * Lấy lý do từ chối phụ lục hợp đồng.
     * Chỉ Admin, Owner hoặc Client của project mới có thể xem. Chỉ áp dụng khi phụ lục đã bị từ chối.
     */
    String getDeclineReason(Long contractId, Authentication auth);
    
    /**
     * Kiểm tra quyền xem phụ lục hợp đồng.
     */
    void ensureCanViewAddendum(Authentication auth, Long contractId);
    
    /**
     * Cập nhật contract và milestones khi phụ lục được thanh toán (PAID).
     * Áp dụng logic cập nhật theo PaymentType (FULL hoặc MILESTONE).
     * 
     * @param addendumId ID của phụ lục đã được thanh toán
     */
    void updateContractAndMilestonesOnAddendumPaid(Long addendumId);
}

