package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "personal_projects")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalProject  extends AbstractEntity<Long>{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "audio_demo_url")
    private String audioDemoUrl;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

}
