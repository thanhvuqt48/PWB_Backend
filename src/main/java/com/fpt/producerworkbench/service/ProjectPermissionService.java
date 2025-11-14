package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.ContractPermissionResponse;
import com.fpt.producerworkbench.dto.response.MilestonePermissionResponse;
import com.fpt.producerworkbench.dto.response.ProjectPermissionResponse;
import org.springframework.security.core.Authentication;

public interface ProjectPermissionService {
    ProjectPermissionResponse checkProjectPermissions(Authentication auth, Long projectId);
    MilestonePermissionResponse checkMilestonePermissions(Authentication auth, Long projectId);
    ContractPermissionResponse checkContractPermissions(Authentication auth, Long projectId);
}
