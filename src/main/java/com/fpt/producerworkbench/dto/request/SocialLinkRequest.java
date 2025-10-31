package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.SocialPlatform;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SocialLinkRequest {
    private SocialPlatform platform;
    private String url;
}
