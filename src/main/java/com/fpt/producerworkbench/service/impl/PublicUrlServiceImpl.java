package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.PublicUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementation of PublicUrlService for converting S3 keys to public CloudFront URLs.
 * 
 * This service generates permanent URLs for public content (portfolio images, avatars).
 * The URLs never expire and can be cached indefinitely.
 * 
 * Configuration required in application.yml:
 * cloudfront:
 *   domain: d123abc456def.cloudfront.net
 *   use-public-urls: true  # Enable public URLs for portfolio/avatar
 */
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
        
        // If already a URL, return as-is
        if (s3Key.startsWith("http://") || s3Key.startsWith("https://")) {
            return s3Key;
        }
        
        // Check if CloudFront domain is configured
        if (cloudfrontDomain == null || cloudfrontDomain.isEmpty()) {
            log.warn("CloudFront domain not configured. Cannot generate public URL for: {}", s3Key);
            // Fallback to presigned URL
            return fileStorageService.generatePresignedUrl(s3Key, false, null);
        }
        
        // Generate public CloudFront URL
        String publicUrl = "https://" + cloudfrontDomain + "/" + s3Key;
        log.debug("Generated public CloudFront URL: {} -> {}", s3Key, publicUrl);
        
        return publicUrl;
    }
    
    @Override
    public boolean isPublicContent(String s3Key) {
        if (s3Key == null || s3Key.isEmpty()) {
            return false;
        }
        
        // Check if it's portfolio or avatar content
        // Pattern: users/{userId}/portfolio/** or users/{userId}/avatar/**
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
        
        // If already a URL, return as-is
        if (s3Key.startsWith("http://") || s3Key.startsWith("https://")) {
            return s3Key;
        }
        
        // Check if public URLs are enabled and content is public
        if (usePublicUrls && isPublicContent(s3Key)) {
            log.debug("Using public URL for: {}", s3Key);
            return toPublicUrl(s3Key);
        }
        
        // Otherwise, use presigned URL (for private content or when public URLs disabled)
        log.debug("Using presigned URL for: {}", s3Key);
        return fileStorageService.generatePresignedUrl(s3Key, false, null);
    }
}

