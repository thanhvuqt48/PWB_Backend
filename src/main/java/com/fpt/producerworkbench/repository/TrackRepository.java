package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.InspirationTrack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRepository extends JpaRepository<InspirationTrack, Long> { }