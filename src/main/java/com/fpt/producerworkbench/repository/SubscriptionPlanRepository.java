package com.fpt.producerworkbench.repository;


import com.fpt.producerworkbench.entity.SubscriptionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<SubscriptionPlan> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    List<SubscriptionPlan> findByCurrency(String currency);

    @Query("SELECT sp FROM SubscriptionPlan sp WHERE sp.durationDays <= :maxDays ORDER BY sp.durationDays ASC")
    List<SubscriptionPlan> findByMaxDurationDays(@Param("maxDays") int maxDays);

    @Query("SELECT sp FROM SubscriptionPlan sp WHERE " +
            "(:name IS NULL OR LOWER(sp.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:currency IS NULL OR sp.currency = :currency) AND " +
            "(:minPrice IS NULL OR sp.price >= :minPrice) AND " +
            "(:maxPrice IS NULL OR sp.price <= :maxPrice)")
    Page<SubscriptionPlan> findWithFilters(@Param("name") String name,
                                           @Param("currency") String currency,
                                           @Param("minPrice") BigDecimal minPrice,
                                           @Param("maxPrice") BigDecimal maxPrice,
                                           Pageable pageable);
}
