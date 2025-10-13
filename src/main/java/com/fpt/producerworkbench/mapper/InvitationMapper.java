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
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(target = "inviterName", ignore = true)
    InvitationResponse toOwnerInvitationResponse(ProjectInvitation invitation);

    // Mapper cho người được mời xem
    @Mapping(source = "id", target = "invitationId")
    @Mapping(source = "project.id", target = "projectId")
    @Mapping(source = "project.title", target = "projectTitle")
    @Mapping(source = "project.creator.fullName", target = "inviterName")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(target = "invitedEmail", ignore = true)
    InvitationResponse toInviteeInvitationResponse(ProjectInvitation invitation);
}