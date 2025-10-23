package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.ProPackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProPackageRepository extends JpaRepository<ProPackage, Long> {

    Optional<ProPackage> findByName(String name);

    List<ProPackage> findByIsActiveTrue();

    List<ProPackage> findByPackageType(ProPackage.ProPackageType packageType);

    List<ProPackage> findByPackageTypeAndIsActiveTrue(ProPackage.ProPackageType packageType);

    @Query("SELECT p FROM ProPackage p WHERE p.isActive = true ORDER BY p.packageType, p.durationMonths")
    List<ProPackage> findAllActiveOrderByTypeAndDuration();

    @Query("SELECT p FROM ProPackage p WHERE " +
           "(:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:packageType IS NULL OR p.packageType = :packageType) AND " +
           "(:isActive IS NULL OR p.isActive = :isActive)")
    Page<ProPackage> findByFilters(@Param("name") String name,
                                   @Param("packageType") ProPackage.ProPackageType packageType,
                                   @Param("isActive") Boolean isActive,
                                   Pageable pageable);

    boolean existsByNameAndIdNot(String name, Long id);
    
    boolean existsByName(String name);
}
