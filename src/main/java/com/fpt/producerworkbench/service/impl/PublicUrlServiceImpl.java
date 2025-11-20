package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.PublicUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicUrlServiceImpl implements PublicUrlService {
    
    private final FileStorageService fileStorageService;
    
    @Value("${cloudfront.domain:}")
    private String cloudfrontDomain;
    
    @Value("${cloudfront.use-public-urls:false}")
    private boolean usePublicUrls;
    
    @Override
    public String toPublicUrl(String s3Key) {
        if (s3Key == null || s3Key.isEmpty()) {
            return null;
        }
        
        if (s3Key.startsWith("http://") || s3Key.startsWith("https://")) {
            return s3Key;
        }
        
        if (cloudfrontDomain == null || cloudfrontDomain.isEmpty()) {
            log.warn("CloudFront domain not configured. Cannot generate public URL for: {}", s3Key);
            return fileStorageService.generatePresignedUrl(s3Key, false, null);
        }
        
        String publicUrl = "https://" + cloudfrontDomain + "/" + s3Key;
        log.debug("Generated public CloudFront URL: {} -> {}", s3Key, publicUrl);
        
        return publicUrl;
    }
    
    @Override
    public boolean isPublicContent(String s3Key) {
        if (s3Key == null || s3Key.isEmpty()) {
            return false;
        }
        
        return s3Key.startsWith("users/") && (
            s3Key.contains("/portfolio/") || 
            s3Key.contains("/avatar/")
        );
    }
    
    @Override
    public String toUrl(String s3Key) {
        if (s3Key == null || s3Key.isEmpty()) {
            return null;
        }
        
        if (s3Key.startsWith("http://") || s3Key.startsWith("https://")) {
            return s3Key;
        }
        
        if (usePublicUrls && isPublicContent(s3Key)) {
            log.debug("Using public URL for: {}", s3Key);
            return toPublicUrl(s3Key);
        }
        
        log.debug("Using presigned URL for: {}", s3Key);
        return fileStorageService.generatePresignedUrl(s3Key, false, null);
    }
}

