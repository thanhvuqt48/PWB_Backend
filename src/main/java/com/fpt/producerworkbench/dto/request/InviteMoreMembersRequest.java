package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.ProjectRole;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class InviteMoreMembersRequest {
    
    @NotEmpty(message = "Danh sách thành viên không được để trống")
    private List<Long> memberIds;
    
    @NotEmpty(message = "Danh sách vai trò không được để trống")
    private List<ProjectRole> roles;
}
