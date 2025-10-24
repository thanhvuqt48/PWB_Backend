package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MediaAttachment implements Serializable {
    @NotBlank(message = "Media URL cannot be blank")
    private String mediaUrl;
    private String mediaName;
    private Long mediaSize;
    private String mediaType;
    private Integer displayOrder;
}
