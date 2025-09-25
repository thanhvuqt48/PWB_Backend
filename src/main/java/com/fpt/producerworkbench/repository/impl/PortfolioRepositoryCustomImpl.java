package com.fpt.producerworkbench.repository.impl;

import com.fpt.producerworkbench.dto.response.PortfolioWithDistanceResponse;
import com.fpt.producerworkbench.entity.Portfolio;
import com.fpt.producerworkbench.repository.PortfolioRepositoryCustom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PortfolioRepositoryCustomImpl implements PortfolioRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public Page<PortfolioWithDistanceResponse> findWithDistance(Specification<Portfolio> spec, Double lat, Double lon, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Portfolio> root = query.from(Portfolio.class);

        Expression<Double> distanceExpression = cb.function("ST_Distance_Sphere", Double.class,
                cb.function("point", Object.class, root.get("longitude"), root.get("latitude")),
                cb.function("point", Object.class, cb.literal(lon), cb.literal(lat))
        );

        query.multiselect(root.alias("portfolio"), distanceExpression.alias("distance"));

        Predicate predicate = spec.toPredicate(root, query, cb);
        query.where(predicate);

        List<Tuple> tuples = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        List<PortfolioWithDistanceResponse> dtos = tuples.stream()
                .map(tuple -> new PortfolioWithDistanceResponse(
                        tuple.get("portfolio", Portfolio.class),
                        tuple.get("distance", Double.class) / 1000
                ))
                .collect(Collectors.toList());

        long total = countQuery(spec);

        return new PageImpl<>(dtos, pageable, total);
    }

    private long countQuery(Specification<Portfolio> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Portfolio> root = query.from(Portfolio.class);
        query.select(cb.count(root));
        Predicate predicate = spec.toPredicate(root, query, cb);
        query.where(predicate);
        return entityManager.createQuery(query).getSingleResult();
    }
}
