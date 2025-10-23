package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.entity.ProPackage;
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
public class ProPackageResponse {

    Long id;
    String name;
    String description;
    BigDecimal price;
    ProPackage.ProPackageType packageType;
    Integer durationMonths;
    Boolean isActive;
    Date createdAt;
    Date updatedAt;
    Long createdBy;
    Long updatedBy;
}
