package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.TaxDeclarationDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaxDeclarationDetailRepository extends JpaRepository<TaxDeclarationDetail, Long> {
    
    List<TaxDeclarationDetail> findByTaxDeclarationId(Long taxDeclarationId);
    
    List<TaxDeclarationDetail> findByUserId(Long userId);
}


