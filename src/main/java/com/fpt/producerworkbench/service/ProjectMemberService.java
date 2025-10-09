package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.ProjectMemberResponse;
import java.util.List;

public interface ProjectMemberService {
    List<ProjectMemberResponse> getProjectMembers(Long projectId);
}