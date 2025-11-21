package com.fpt.producerworkbench.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorIndexResponse {
    private Boolean success;
    private String message;
    private Integer totalIndexed;
    private Integer failed;
}
