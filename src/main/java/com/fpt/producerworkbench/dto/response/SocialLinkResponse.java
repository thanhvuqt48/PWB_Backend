package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.SocialPlatform;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SocialLinkResponse {

    private Long id;
    private SocialPlatform platform;
    private String url;
}
