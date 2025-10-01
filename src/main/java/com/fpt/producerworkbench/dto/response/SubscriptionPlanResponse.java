package com.fpt.producerworkbench.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.Date;


@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubscriptionPlanResponse {

    Long id;
    String name;
    BigDecimal price;
    String currency;
    int durationDays;
    String description;
    Date createdAt;
    Date updatedAt;
    Long createdBy;
    Long updatedBy;
}