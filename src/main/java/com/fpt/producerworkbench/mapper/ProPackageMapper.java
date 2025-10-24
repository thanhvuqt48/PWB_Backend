package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.request.ProPackageRequest;
import com.fpt.producerworkbench.dto.response.ProPackageResponse;
import com.fpt.producerworkbench.entity.ProPackage;
import org.springframework.stereotype.Component;

@Component
public class ProPackageMapper {

    public ProPackage toEntity(ProPackageRequest request) {
        Integer durationMonths = request.getPackageType() == ProPackage.ProPackageType.MONTHLY ? 1 : 12;
        
        return ProPackage.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .packageType(request.getPackageType())
                .durationMonths(durationMonths)
                .isActive(request.getIsActive())
                .build();
    }

    public ProPackageResponse toResponse(ProPackage proPackage) {
        return ProPackageResponse.builder()
                .id(proPackage.getId())
                .name(proPackage.getName())
                .description(proPackage.getDescription())
                .price(proPackage.getPrice())
                .packageType(proPackage.getPackageType())
                .durationMonths(proPackage.getDurationMonths())
                .isActive(proPackage.getIsActive())
                .createdAt(proPackage.getCreatedAt())
                .updatedAt(proPackage.getUpdatedAt())
                .createdBy(proPackage.getCreatedBy())
                .updatedBy(proPackage.getUpdatedBy())
                .build();
    }

    public void updateEntityFromRequest(ProPackageRequest request, ProPackage entity) {
        Integer durationMonths = request.getPackageType() == ProPackage.ProPackageType.MONTHLY ? 1 : 12;
        
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setPrice(request.getPrice());
        entity.setPackageType(request.getPackageType());
        entity.setDurationMonths(durationMonths);
        entity.setIsActive(request.getIsActive());
    }
}
