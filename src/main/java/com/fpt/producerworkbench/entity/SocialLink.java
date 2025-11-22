package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.SocialPlatform;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "social_links")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialLink extends AbstractEntity<Long>{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    private SocialPlatform platform;

    @Column(nullable = false, length = 1024)
    private String url;
}