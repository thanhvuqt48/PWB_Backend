package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.ContractComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractCommentRepository extends JpaRepository<ContractComment, Long> {
}