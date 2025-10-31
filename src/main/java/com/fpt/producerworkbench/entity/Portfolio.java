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
public class Portfolio extends AbstractEntity<Long> {

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

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PortfolioSection> sections;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PersonalProject> personalProjects;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SocialLink> socialLinks;

    @ManyToMany
    @JoinTable(
            name = "portfolio_genres",
            joinColumns = @JoinColumn(name = "portfolio_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "genre_id"})
    )
    private Set<Genre> genres;

    @ManyToMany
    @JoinTable(
            name = "portfolio_tags",
            joinColumns = @JoinColumn(name = "portfolio_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "tag_id"})
    )
    private Set<Tag> tags;
}