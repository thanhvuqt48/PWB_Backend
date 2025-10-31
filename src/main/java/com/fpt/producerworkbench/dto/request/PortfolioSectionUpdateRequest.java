package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.PortfolioSectionType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PortfolioSectionUpdateRequest {
    private Long id;
    private String title;
    private String content;
    private int displayOrder;
    private PortfolioSectionType sectionType;
}
