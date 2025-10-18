package com.fpt.producerworkbench.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class FileMetaDataResponse {
    private String name;
    private String contentType;
    private long size;
    private String url;
    private Integer displayOrder;
}
