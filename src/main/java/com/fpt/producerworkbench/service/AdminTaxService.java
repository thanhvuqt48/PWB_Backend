package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.dto.response.AdminTaxOverviewResponse;
import com.fpt.producerworkbench.dto.response.AdminTaxPayoutResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface AdminTaxService {
    AdminTaxOverviewResponse getOverview(LocalDate from, LocalDate to, String groupBy);

    Page<AdminTaxPayoutResponse> getPayouts(
            LocalDate from,
            LocalDate to,
            Integer month,
            Integer year,
            Integer quarter,
            Long userId,
            Long projectId,
            Long contractId,
            PayoutSource source,
            Boolean declared,
            Boolean paid,
            Pageable pageable
    );

    ExportedFile exportPayouts(
            LocalDate from,
            LocalDate to,
            Integer month,
            Integer year,
            Integer quarter,
            Long userId,
            Long projectId,
            Long contractId,
            PayoutSource source,
            Boolean declared,
            Boolean paid,
            String format
    );

    void markPayouts(List<Long> ids, Boolean declared, Boolean paid);

    class ExportedFile {
        public final byte[] bytes;
        public final String filename;
        public final String contentType;

        public ExportedFile(byte[] bytes, String filename, String contentType) {
            this.bytes = bytes;
            this.filename = filename;
            this.contentType = contentType;
        }
    }
}

