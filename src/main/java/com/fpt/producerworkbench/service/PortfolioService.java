package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.PortfolioRequest;
import com.fpt.producerworkbench.dto.response.PortfolioResponse;
import org.springframework.web.multipart.MultipartFile;

public interface PortfolioService {

    PortfolioResponse create(PortfolioRequest request, MultipartFile coverImage);

    PortfolioResponse findById(Long id);

    PortfolioResponse update(Long id, PortfolioRequest request);

}
