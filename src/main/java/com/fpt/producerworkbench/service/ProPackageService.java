package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ProPackageRequest;
import com.fpt.producerworkbench.dto.response.ProPackageResponse;
import com.fpt.producerworkbench.entity.ProPackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProPackageService {
    
    ProPackageResponse create(ProPackageRequest request);
    
    ProPackageResponse findById(Long id);
    
    Page<ProPackageResponse> findAll(Pageable pageable);
    
    Page<ProPackageResponse> findWithFilters(String name, 
                                           ProPackage.ProPackageType packageType, 
                                           Boolean isActive, 
                                           Pageable pageable);
    
    List<ProPackageResponse> findAllActive();
    
    List<ProPackageResponse> findByPackageType(ProPackage.ProPackageType packageType);
    
    List<ProPackageResponse> findByPackageTypeAndActive(ProPackage.ProPackageType packageType);
    
    ProPackageResponse update(Long id, ProPackageRequest request);
    
    void delete(Long id);
    
    boolean existsByName(String name);
    
    boolean existsByNameAndIdNot(String name, Long id);
}
