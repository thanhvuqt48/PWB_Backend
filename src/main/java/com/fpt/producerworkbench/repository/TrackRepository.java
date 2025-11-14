package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRepository extends JpaRepository<Track, Long> { }