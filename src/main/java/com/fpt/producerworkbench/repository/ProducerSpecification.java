package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.List;

public class ProducerSpecification {

    public static Specification<Portfolio> hasName(String name) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(name)) {
                return null;
            }
            Join<Portfolio, User> userJoin = root.join("user");
            String pattern = "%" + name.toLowerCase() + "%";
            Predicate firstNameMatch = criteriaBuilder.like(criteriaBuilder.lower(userJoin.get("firstName")), pattern);
            Predicate lastNameMatch = criteriaBuilder.like(criteriaBuilder.lower(userJoin.get("lastName")), pattern);
            return criteriaBuilder.or(firstNameMatch, lastNameMatch);
        };
    }

    public static Specification<Portfolio> hasGenres(List<Integer> genreIds) {
        return (root, query, criteriaBuilder) -> {
            if (genreIds == null || genreIds.isEmpty()) {
                return null;
            }
            query.distinct(true);
            Join<Portfolio, PortfolioGenre> portfolioGenreJoin = root.join("portfolioGenres");
            Join<PortfolioGenre, Genre> genreJoin = portfolioGenreJoin.join("genre");
            return genreJoin.get("id").in(genreIds);
        };
    }

    public static Specification<Portfolio> hasTags(List<String> tagNames) {
        return (root, query, criteriaBuilder) -> {
            if (tagNames == null || tagNames.isEmpty()) {
                return null;
            }
            query.distinct(true);
            Join<Portfolio, PortfolioTag> portfolioTagJoin = root.join("portfolioTags");
            Join<PortfolioTag, Tag> tagJoin = portfolioTagJoin.join("tag");
            return tagJoin.get("name").in(tagNames);
        };
    }

    public static Specification<Portfolio> isWithinRadius(Double lat, Double lon, Double radiusInKm) {
        return (root, query, criteriaBuilder) -> {
            if (lat == null || lon == null || radiusInKm == null || radiusInKm <= 0) {
                return null;
            }

            return criteriaBuilder.lessThanOrEqualTo(
                    criteriaBuilder.function("ST_Distance_Sphere", Double.class,
                            criteriaBuilder.function("point", Object.class, root.get("longitude"), root.get("latitude")),
                            criteriaBuilder.function("point", Object.class, criteriaBuilder.literal(lon), criteriaBuilder.literal(lat))
                    ),
                    radiusInKm * 1000
            );
        };
    }

    public static Specification<Portfolio> hasGenresOrTags(List<String> names) {
        return (root, query, criteriaBuilder) -> {
            if (names == null || names.isEmpty()) {
                return null;
            }

            query.distinct(true);

            Join<Portfolio, PortfolioGenre> portfolioGenreJoin = root.join("portfolioGenres");
            Join<PortfolioGenre, Genre> genreJoin = portfolioGenreJoin.join("genre");
            Predicate genrePredicate = genreJoin.get("name").in(names);

            Join<Portfolio, PortfolioTag> portfolioTagJoin = root.join("portfolioTags");
            Join<PortfolioTag, Tag> tagJoin = portfolioTagJoin.join("tag");
            Predicate tagPredicate = tagJoin.get("name").in(names);

            return criteriaBuilder.or(genrePredicate, tagPredicate);
        };
    }

}