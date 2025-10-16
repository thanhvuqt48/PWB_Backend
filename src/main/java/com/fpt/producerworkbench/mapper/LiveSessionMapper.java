package com.fpt.producerworkbench.mapper;


import com.fpt.producerworkbench.dto.response.LiveSessionResponse;
import com.fpt.producerworkbench.entity.LiveSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LiveSessionMapper {

    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "project.title", target = "projectName")
    @Mapping(source = "host.id", target = "hostId")
    @Mapping(source = "host", target = "hostName", qualifiedByName = "mapHostName")
    @Mapping(source = "id", target = "id")
    LiveSessionResponse toDTO(LiveSession session);

    List<LiveSessionResponse> toDTOList(List<LiveSession> sessions);

    @Named("mapHostName")
    default String mapHostName(com.fpt.producerworkbench.entity.User host) {
        if (host == null) return null;
        return host.getFirstName() + " " + host.getLastName();
    }
}
