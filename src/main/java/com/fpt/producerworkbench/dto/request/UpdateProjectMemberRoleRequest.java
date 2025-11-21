package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.ProjectRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProjectMemberRoleRequest {

    @NotNull(message = "projectRole không được để trống.")
    private ProjectRole projectRole;
}


