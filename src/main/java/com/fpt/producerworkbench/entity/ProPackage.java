package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pro_packages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProPackage extends AbstractEntity<Long> {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false)
    private ProPackageType packageType;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    public enum ProPackageType {
        MONTHLY,
        YEARLY
    }
}
