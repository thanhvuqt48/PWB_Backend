package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PackageSalesStat {
    private String packageName;
    private Long totalSold;
}