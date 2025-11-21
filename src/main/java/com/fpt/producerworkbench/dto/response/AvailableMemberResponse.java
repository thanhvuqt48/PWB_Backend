package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.ProjectRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableMemberResponse {
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private ProjectRole projectRole;
}
