package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.WithdrawalStatus;
import com.fpt.producerworkbench.entity.Withdrawal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class WithdrawalSpecification {

    public static Specification<Withdrawal> hasKeyword(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            Predicate codeMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("withdrawalCode")), pattern);
            Predicate accountNumberMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("accountNumber")), pattern);
            Predicate accountHolderMatch = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("accountHolderName")), pattern);
            return criteriaBuilder.or(codeMatch, accountNumberMatch, accountHolderMatch);
        };
    }

    public static Specification<Withdrawal> hasStatus(WithdrawalStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return null;
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    public static Specification<Withdrawal> hasUserId(Long userId) {
        return (root, query, criteriaBuilder) -> {
            if (userId == null) {
                return null;
            }
            return criteriaBuilder.equal(root.get("user").get("id"), userId);
        };
    }

    public static Specification<Withdrawal> hasAmountBetween(BigDecimal minAmount, BigDecimal maxAmount) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minAmount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }
            if (predicates.isEmpty()) {
                return null;
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Withdrawal> hasCreatedAtBetween(Date fromDate, Date toDate) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }
            if (predicates.isEmpty()) {
                return null;
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}

