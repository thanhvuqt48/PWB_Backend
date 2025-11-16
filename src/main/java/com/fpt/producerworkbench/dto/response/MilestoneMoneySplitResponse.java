package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fpt.producerworkbench.common.MoneySplitStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneMoneySplitResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("userEmail")
    private String userEmail;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("status")
    private MoneySplitStatus status;

    @JsonProperty("note")
    private String note;

    @JsonProperty("rejectionReason")
    private String rejectionReason;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    @JsonProperty("isCurrentUserRecipient")
    private Boolean isCurrentUserRecipient;
}


