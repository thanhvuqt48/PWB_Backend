package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.entity.TaxPayoutRecord;
import com.fpt.producerworkbench.repository.TaxPayoutRecordRepository;
import com.fpt.producerworkbench.service.TaxPayoutRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxPayoutRecordServiceImpl implements TaxPayoutRecordService {
    
    private final TaxPayoutRecordRepository taxPayoutRecordRepository;
    
    @Override
    @Transactional(readOnly = true)
    public Page<TaxPayoutRecord> listRecords(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate,
            Boolean isDeclared,
            Pageable pageable
    ) {
        return taxPayoutRecordRepository.findByFilters(
                userId, fromDate, toDate, isDeclared, pageable);
    }
    
    @Override
    @Transactional
    public void markAsDeclared(List<Long> payoutIds, Long declarationId) {
        log.info("Marking {} payouts as declared for declaration: {}", 
                payoutIds.size(), declarationId);
        
        List<TaxPayoutRecord> records = taxPayoutRecordRepository.findAllById(payoutIds);
        
        for (TaxPayoutRecord record : records) {
            record.setIsTaxDeclared(true);
            record.setTaxDeclarationId(declarationId);
            record.setTaxDeclarationDate(LocalDate.now());
        }
        
        taxPayoutRecordRepository.saveAll(records);
        log.info("Marked {} payouts as declared", records.size());
    }
}


