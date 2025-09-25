package com.fpt.producerworkbench.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "genres")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Genre extends AbstractEntity<Integer> {

    @Column(nullable = false, unique = true)
    private String name;
}