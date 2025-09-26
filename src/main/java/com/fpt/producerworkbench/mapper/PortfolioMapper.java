package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.response.ProducerSummaryResponse;
import com.fpt.producerworkbench.entity.Portfolio;
import com.fpt.producerworkbench.entity.PortfolioGenre;
import com.fpt.producerworkbench.entity.PortfolioTag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    @Mapping(source = "portfolio.user.id", target = "userId")
    @Mapping(source = "portfolio.user.fullName", target = "fullName")
    @Mapping(source = "portfolio.user.avatarUrl", target = "avatarUrl")
    @Mapping(source = "portfolio.user.location", target = "location")
    @Mapping(source = "portfolio.portfolioGenres", target = "genres", qualifiedByName = "genresToStrings")
    @Mapping(source = "portfolio.portfolioTags", target = "tags", qualifiedByName = "tagsToStrings")
    @Mapping(source = "distanceInKm", target = "distanceInKm")
    ProducerSummaryResponse toProducerSummaryResponse(Portfolio portfolio, Double distanceInKm);

    @Named("genresToStrings")
    default Set<String> genresToStrings(Set<PortfolioGenre> portfolioGenres) {
        if (portfolioGenres == null || portfolioGenres.isEmpty()) {
            return Collections.emptySet();
        }
        return portfolioGenres.stream()
                .map(pg -> pg.getGenre().getName())
                .collect(Collectors.toSet());
    }

    @Named("tagsToStrings")
    default Set<String> tagsToStrings(Set<PortfolioTag> portfolioTags) {
        if (portfolioTags == null || portfolioTags.isEmpty()) {
            return Collections.emptySet();
        }
        return portfolioTags.stream()
                .map(pt -> pt.getTag().getName())
                .collect(Collectors.toSet());
    }
}
