package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {}
