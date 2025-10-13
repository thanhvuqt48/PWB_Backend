package com.fpt.producerworkbench.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartSigningResponse {

    private String inviteId;

    private String embeddedLink;
}
