package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveRejectMoneySplitRequest {

    @JsonProperty("rejectionReason")
    private String rejectionReason;
}


