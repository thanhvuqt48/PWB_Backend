package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.request.PortfolioRequest;
import com.fpt.producerworkbench.dto.response.PortfolioResponse;
import com.fpt.producerworkbench.dto.response.ProducerSummaryResponse;
import com.fpt.producerworkbench.entity.Genre;
import com.fpt.producerworkbench.entity.Portfolio;
import com.fpt.producerworkbench.entity.Tag;
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
    @Mapping(source = "portfolio.genres", target = "genres", qualifiedByName = "genresToStrings")
    @Mapping(source = "portfolio.tags", target = "tags", qualifiedByName = "tagsToStrings")
    @Mapping(source = "distanceInKm", target = "distanceInKm")
    ProducerSummaryResponse toProducerSummaryResponse(Portfolio portfolio, Double distanceInKm);

    @Named("genresToStrings")
    default Set<String> genresToStrings(Set<Genre> genres) {
        if (genres == null || genres.isEmpty()) {
            return Collections.emptySet();
        }
        return genres.stream()
                .map(Genre::getName)
                .collect(Collectors.toSet());
    }

    @Named("tagsToStrings")
    default Set<String> tagsToStrings(Set<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptySet();
        }
        return tags.stream()
                .map(Tag::getName)
                .collect(Collectors.toSet());
    }

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.firstName", target = "firstName")
    @Mapping(source = "user.lastName", target = "lastName")
    @Mapping(source = "user.avatarUrl", target = "avatarUrl")
    @Mapping(source = "genres", target = "genres", qualifiedByName = "genresToStrings")
    @Mapping(source = "tags", target = "tags", qualifiedByName = "tagsToStrings")
    PortfolioResponse toPortfolioResponse(Portfolio portfolio);
}
