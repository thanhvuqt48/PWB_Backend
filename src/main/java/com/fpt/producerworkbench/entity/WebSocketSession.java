package com.fpt.producerworkbench.entity;

import lombok.*;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class WebSocketSession implements Serializable {
    private String socketSessionId;
    private String userId;
}
