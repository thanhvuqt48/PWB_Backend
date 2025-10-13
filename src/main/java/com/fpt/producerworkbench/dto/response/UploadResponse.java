package com.fpt.producerworkbench.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadResponse {
    private String objectKey;
    private String url;
}