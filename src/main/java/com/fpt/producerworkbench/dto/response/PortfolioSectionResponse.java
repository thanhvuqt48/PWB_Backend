package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.PortfolioSectionType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PortfolioSectionResponse {

    private Long id;
    private String title;
    private String content;
    private int displayOrder;
    private PortfolioSectionType sectionType;
}
