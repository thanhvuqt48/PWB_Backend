package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Table(name = "portfolios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio extends AbstractEntity<Long>{

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(name = "custom_url_slug", unique = true)
    private String customUrlSlug;

    private String headline;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "is_public")
    private boolean isPublic;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PortfolioSection> sections;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PersonalProject> personalProjects;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SocialLink> socialLinks;
}