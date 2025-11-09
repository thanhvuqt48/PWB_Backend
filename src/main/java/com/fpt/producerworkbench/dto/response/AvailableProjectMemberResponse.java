package com.fpt.producerworkbench.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableProjectMemberResponse {

    private Long userId;
    private String userName;
    private String userEmail;
    private String projectRole; // COLLABORATOR, OBSERVER, etc.
}

