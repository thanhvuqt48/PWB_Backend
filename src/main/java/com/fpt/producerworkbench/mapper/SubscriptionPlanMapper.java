package com.fpt.producerworkbench.mapper;
import com.fpt.producerworkbench.dto.request.SubscriptionPlanRequest;
import com.fpt.producerworkbench.dto.response.SubscriptionPlanResponse;
import com.fpt.producerworkbench.entity.SubscriptionPlan;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPlanMapper {
    public SubscriptionPlan toEntity(SubscriptionPlanRequest request) {
        if (request == null) return null;

        return SubscriptionPlan.builder()
                .name(request.getName())
                .price(request.getPrice())
                .currency(request.getCurrency())
                .durationDays(request.getDurationDays())
                .description(request.getDescription())
                .build();
    }

    public SubscriptionPlanResponse toResponse(SubscriptionPlan entity) {
        if (entity == null) return null;

        return SubscriptionPlanResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .price(entity.getPrice())
                .currency(entity.getCurrency())
                .durationDays(entity.getDurationDays())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    public void updateEntityFromRequest(SubscriptionPlanRequest request, SubscriptionPlan entity) {
        if (request == null || entity == null) return;

        entity.setName(request.getName());
        entity.setPrice(request.getPrice());
        entity.setCurrency(request.getCurrency());
        entity.setDurationDays(request.getDurationDays());
        entity.setDescription(request.getDescription());
    }
}
