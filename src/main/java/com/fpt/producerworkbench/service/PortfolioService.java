package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.PortfolioRequest;
import com.fpt.producerworkbench.dto.request.PortfolioUpdateRequest;
import com.fpt.producerworkbench.dto.response.PortfolioResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface PortfolioService {

    PortfolioResponse create(PortfolioRequest request, MultipartFile coverImage);

    PortfolioResponse findById(Long id);

    PortfolioResponse updatePersonalPortfolio(
            PortfolioUpdateRequest request,
            MultipartFile coverImage,
            Map<String, MultipartFile> projectAudioDemos,
            Map<String, MultipartFile> projectCoverImages
    );

    PortfolioResponse getPersonalPortfolio();

    PortfolioResponse getPortfolioByUserId(Long userId);

    PortfolioResponse getPortfolioByCustomUrlSlug(String slug);

}
