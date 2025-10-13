//Gốc
//package com.fpt.producerworkbench.controller;
//
//import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
//import com.fpt.producerworkbench.service.ContractPdfService;
//import jakarta.servlet.http.HttpServletRequest; // *** THÊM IMPORT NÀY ***
//import lombok.AccessLevel;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
//import lombok.extern.slf4j.Slf4j; // *** THÊM IMPORT NÀY ***
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.Authentication;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("/api/v1/contracts/pdf")
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//@Slf4j
//public class ContractPdfController {
//
//    ContractPdfService contractPdfService;
//
//    @PostMapping(value = "fill", produces = "application/pdf")
//    public ResponseEntity<byte[]> fill(
//            Authentication auth,
//            @RequestBody ContractPdfFillRequest req,
//            HttpServletRequest request // *** THÊM THAM SỐ NÀY ***
//    ) {
//        // *** THÊM ĐOẠN LOG CHẨN ĐOÁN VÀO ĐÂY ***
//        log.info("================= REQUEST ENCODING DIAGNOSTICS =================");
//        log.info("Request Character Encoding from Servlet: {}", request.getCharacterEncoding());
//        log.info("Content-Type Header from Servlet: {}", request.getHeader("Content-Type"));
//        log.info("================================================================");
//
//        // Code cũ của bạn để gọi service
//        byte[] pdf = contractPdfService.fillTemplate(auth, req);
//
//        return ResponseEntity.ok()
//                .header("Content-Disposition", "inline; filename=contract.pdf")
//                .body(pdf);
//    }
//}
package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ContractPdfFillRequest;
import com.fpt.producerworkbench.service.ContractPdfService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contracts/pdf")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ContractPdfController {

    ContractPdfService contractPdfService;

    @PostMapping(value = "fill", produces = "application/pdf")
//    @PreAuthorize("hasRole('OWNER')") // chỉ OWNER mới được tạo
    public ResponseEntity<byte[]> fill(
            Authentication auth,
            @Valid @RequestBody ContractPdfFillRequest req,
            HttpServletRequest request
    ) {
        byte[] pdf = contractPdfService.fillTemplate(auth, req);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=contract.pdf")
                .body(pdf);
    }
}

