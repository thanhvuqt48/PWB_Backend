package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.response.ProjectMemberResponse;
import com.fpt.producerworkbench.entity.ProjectMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectMemberMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.fullName", target = "fullName")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.avatarUrl", target = "avatarUrl")
    @Mapping(source = "projectRole", target = "role")
    @Mapping(source = "anonymous", target = "anonymous")
    ProjectMemberResponse toResponse(ProjectMember member);
}