package com.fpt.producerworkbench.entity;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(
name = "follows",
uniqueConstraints = {
@UniqueConstraint(name = "uq_follows", columnNames = {"follower_id", "followee_id"})
}
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Follow extends AbstractEntity<Long> {


@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "follower_id", nullable = false,
foreignKey = @ForeignKey(name = "fk_follows_follower"))
private User follower; 


@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "followee_id", nullable = false,
foreignKey = @ForeignKey(name = "fk_follows_followee"))
private User followee; 
}
