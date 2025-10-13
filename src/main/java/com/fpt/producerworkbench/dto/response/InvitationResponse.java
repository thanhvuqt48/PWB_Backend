package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.InvitationStatus;
import com.fpt.producerworkbench.common.ProjectRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {
    private Long invitationId;
    private Long projectId;
    private String projectTitle;
    private ProjectRole invitedRole;
    private InvitationStatus status;
    private LocalDateTime expiresAt;
    private Date createdAt;

    private String invitedEmail;

    private String inviterName;
}