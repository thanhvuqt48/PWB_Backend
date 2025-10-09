package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.response.InvitationResponse;
import com.fpt.producerworkbench.entity.ProjectInvitation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvitationMapper {

    // Mapper cho Owner xem
    @Mapping(source = "id", target = "invitationId")
    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "project.title", target = "projectTitle")
    @Mapping(source = "email", target = "invitedEmail")
    InvitationResponse toOwnerInvitationResponse(ProjectInvitation invitation);

    // Mapper cho người được mời xem
    @Mapping(source = "id", target = "invitationId")
    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "project.title", target = "projectTitle")
    @Mapping(source = "project.creator.fullName", target = "inviterName")
    InvitationResponse toInviteeInvitationResponse(ProjectInvitation invitation);
}