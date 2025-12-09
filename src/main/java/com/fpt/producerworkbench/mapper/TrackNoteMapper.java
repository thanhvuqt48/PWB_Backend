package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.response.TrackNoteResponse;
import com.fpt.producerworkbench.entity.TrackNote;
import com.fpt.producerworkbench.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TrackNoteMapper {

    @Mapping(source = "track.id", target = "trackId")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user", target = "userName", qualifiedByName = "mapUserName")
    @Mapping(source = "user.avatarUrl", target = "userAvatar")
    @Mapping(source = "timestamp", target = "timestamp")
    TrackNoteResponse toDTO(TrackNote note);

    List<TrackNoteResponse> toDTOList(List<TrackNote> notes);

    @Named("mapUserName")
    default String mapUserName(User user) {
        if (user == null) return null;
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }
}
