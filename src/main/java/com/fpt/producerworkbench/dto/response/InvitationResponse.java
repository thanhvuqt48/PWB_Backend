package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.ProjectRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {
    // Dành cho cả hai phía
    private Long invitationId;
    private Long projectId;
    private String projectTitle;
    private ProjectRole invitedRole;
    private InvitationStatus status;
    private LocalDateTime expiresAt;

    // Chỉ dành cho Owner xem
    private String invitedEmail;

    // Chỉ dành cho người được mời xem
    private String inviterName;
}