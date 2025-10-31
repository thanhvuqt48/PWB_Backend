package com.fpt.producerworkbench.service;

/**
 * Service for converting S3 keys to public CloudFront URLs.
 * 
 * This service is used for public content (portfolio images, avatars) that don't need
 * access control. The generated URLs never expire and can be cached by browsers/CDN.
 * 
 * Benefits:
 * - No expiration (URLs work forever)
 * - Better performance (CloudFront caching)
 * - SEO friendly (stable URLs)
 * - Simpler frontend (no need to refresh URLs)
 * 
 * Security Note:
 * Only use this for truly public content. For sensitive files (contracts, private docs),
 * continue using presigned URLs from FileStorageService.
 */
public interface PublicUrlService {
    
    /**
     * Convert S3 key to public CloudFront URL (no expiration).
     * 
     * Example:
     * Input:  "users/1/portfolio/cover/abc-123.jpg"
     * Output: "https://d123abc.cloudfront.net/users/1/portfolio/cover/abc-123.jpg"
     * 
     * @param s3Key The S3 object key
     * @return Public CloudFront URL, or null if s3Key is null/empty
     */
    String toPublicUrl(String s3Key);
    
    /**
     * Check if S3 key is for public content (portfolio/avatar).
     * 
     * Public content patterns:
     * - users/{userId}/portfolio/**
     * - users/{userId}/avatar/**
     * 
     * @param s3Key The S3 object key
     * @return true if the key is for public content
     */
    boolean isPublicContent(String s3Key);
    
    /**
     * Convert S3 key to URL, using public URL if content is public,
     * otherwise using presigned URL.
     * 
     * This is a smart method that automatically chooses the right URL type
     * based on the content type.
     * 
     * @param s3Key The S3 object key
     * @return Public URL for public content, presigned URL for private content
     */
    String toUrl(String s3Key);
}

