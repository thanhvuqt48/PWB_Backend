package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankRepository extends JpaRepository<Bank, Long> {
    
    List<Bank> findByTransferSupportedTrueOrderByNameAsc();
    
    @Query("SELECT b FROM Bank b WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(b.shortName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY b.name ASC")
    List<Bank> searchBanks(@Param("keyword") String keyword);
}

