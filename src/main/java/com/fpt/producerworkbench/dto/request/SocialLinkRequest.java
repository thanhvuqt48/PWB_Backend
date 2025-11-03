package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.SocialPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SocialLinkRequest {
    @NotNull(message = "Platform không được để trống")
    private SocialPlatform platform;

    @NotBlank(message = "URL không được để trống")
    private String url;
}
