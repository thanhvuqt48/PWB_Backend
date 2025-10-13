package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import org.springframework.security.core.Authentication;

public interface ContractPdfService {

    //Điền dữ liệu vào PDF mẫu và trả về mảng bytes PDF đã fill (đã flatten).
    byte[] fillTemplate(Authentication auth, ContractPdfFillRequest req);
}
