package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.ConversationType;
import com.fpt.producerworkbench.common.MilestoneChatType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class  ConversationCreationResponse {
    private String id;
    private ConversationType conversationType;
    private String participantHash;
    private String conversationAvatar;
    private String conversationName;
    private List<ParticipantInfoDetailResponse> participantInfo;
    private LocalDateTime createdAt;
    private MilestoneChatType milestoneChatType; // INTERNAL hoặc CLIENT - chỉ có giá trị khi là group chat của milestone
}
