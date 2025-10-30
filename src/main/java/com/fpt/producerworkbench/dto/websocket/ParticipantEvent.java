package com.fpt.producerworkbench.dto.websocket;

import com.fpt.producerworkbench.common.ParticipantRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantEvent {

    private String action; // JOINED, LEFT, PERMISSIONS_UPDATED, REMOVED, MUTED, UNMUTED
    private Long userId;
    private String userName;
    private String userAvatarUrl;
    private ParticipantRole role;
    private Boolean isOnline;
    private Boolean audioEnabled;
    private Boolean videoEnabled;
    private Integer currentParticipants;
}
