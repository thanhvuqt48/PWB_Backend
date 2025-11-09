package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.MilestoneExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MilestoneExpenseRepository extends JpaRepository<MilestoneExpense, Long> {

    List<MilestoneExpense> findByMilestoneId(Long milestoneId);
}


