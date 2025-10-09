package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.response.ProjectResponse;
import com.fpt.producerworkbench.entity.Project;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    @Mapping(source = "creator.id", target = "creatorId")
    ProjectResponse toProjectResponse(Project project);

}