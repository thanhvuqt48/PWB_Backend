package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.response.SessionParticipantResponse;
import com.fpt.producerworkbench.entity.SessionParticipant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SessionParticipantMapper {

    @Mapping(source = "session.id", target = "sessionId")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user", target = "userName", qualifiedByName = "mapUserName")
    @Mapping(source = "user.email", target = "userEmail")
    @Mapping(source = "user.avatarUrl", target = "userAvatarUrl")
    SessionParticipantResponse toDTO(SessionParticipant participant);

    List<SessionParticipantResponse> toDTOList(List<SessionParticipant> participants);

    @Named("mapUserName")
    default String mapUserName(com.fpt.producerworkbench.entity.User user) {
        if (user == null) return null;
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }
}
