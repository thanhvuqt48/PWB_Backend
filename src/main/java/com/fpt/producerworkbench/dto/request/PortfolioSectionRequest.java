package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.PortfolioSectionType;
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
public class PortfolioSectionRequest {
    @NotBlank(message = "Title không được để trống")
    private String title;

    @NotBlank(message = "Content không được để trống")
    private String content;

    private int displayOrder;

    @NotNull(message = "Section type không được để trống")
    private PortfolioSectionType sectionType;
}
