package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.entity.Portfolio;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PortfolioWithDistanceResponse {
    private Portfolio portfolio;
    private Double distanceInKm;
}
