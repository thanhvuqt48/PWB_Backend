package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.ProjectRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    private LocalDateTime scheduledStart; // Optional, null = start now

    private List<Long> preloadedFileIds; // Optional, files to share in session

    // ========== Invite Members (Optional) ==========
    // If provided, will send email invitations immediately after creating session
    
    private List<Long> invitedMemberIds; // Specific member IDs to invite
    
    private List<ProjectRole> inviteRoles; // Or invite all members with these roles
    
    // ========== Visibility ==========
    // Will be auto-calculated: if invitedMemberIds or inviteRoles provided -> isPublic = false
    // Otherwise -> isPublic = true (PUBLIC session)
    // Frontend doesn't need to send this field
}
