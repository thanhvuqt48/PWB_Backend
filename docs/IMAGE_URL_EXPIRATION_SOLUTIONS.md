# 🔐 Giải pháp cho vấn đề Presigned URL Expiration

## ❓ Vấn đề

**User mở trang web và để lâu (> 24 giờ) → Presigned URLs expire → Ảnh bị lỗi 403 Forbidden**

```
User opens portfolio page at 9:00 AM
  ↓
Presigned URLs valid until 9:00 AM next day (24h expiration)
  ↓
User leaves tab open and comes back at 10:00 AM next day
  ↓
❌ All images show 403 Forbidden error
```

---

## 📋 3 Giải pháp

### **Cách 1: Tăng thời gian expire của Presigned URL ⭐ (Đã implement)**

**Mô tả:** Tăng expiration time từ 15 phút lên 24 giờ hoặc 7 ngày.

#### **✅ Ưu điểm:**
- Đơn giản nhất, không cần thay đổi frontend
- User có thể xem ảnh lâu mà không bị lỗi
- Không cần config thêm AWS

#### **⚠️ Nhược điểm:**
- URL vẫn có thể expire nếu user để quá lâu (> 24h)
- URL có thể bị share và dùng trong thời gian expire (security risk nhỏ)
- Nếu xóa file trên S3, URL vẫn còn valid cho đến khi expire

#### **📝 Implementation:**

<augment_code_snippet path="src/main/java/com/fpt/producerworkbench/service/impl/S3ServiceImpl.java" mode="EXCERPT">
```java
// For view/display: 24 hours expiration (user can stay on page long time)
// For download: can use shorter duration if needed
Duration expiration = forDownload ? Duration.ofMinutes(15) : Duration.ofHours(24);

GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(expiration)
        .getObjectRequest(requestBuilder.build())
        .build();
```
</augment_code_snippet>

#### **🎯 Khi nào dùng:**
- ✅ MVP / Development phase
- ✅ Khi chưa có thời gian setup CloudFront
- ✅ Khi security không phải concern lớn (portfolio images thường là public)

#### **⚙️ Config options:**
```java
// Conservative: 1 hour
Duration.ofHours(1)

// Balanced: 24 hours (current implementation)
Duration.ofHours(24)

// Aggressive: 7 days
Duration.ofDays(7)

// Maximum: 7 days (AWS limit for presigned URLs)
Duration.ofDays(7)
```

---

### **Cách 2: Dùng CloudFront Signed URLs 🚀**

**Mô tả:** Thay vì S3 presigned URLs, dùng CloudFront signed URLs với expiration lâu hơn.

#### **✅ Ưu điểm:**
- Expire lâu hơn (lên đến 7 ngày hoặc custom policy)
- Faster loading (CloudFront CDN caching)
- Better security với CloudFront key pairs
- Có thể set custom policies (IP restrictions, time windows)

#### **⚠️ Nhược điểm:**
- Phức tạp hơn, cần setup CloudFront key pairs
- Cần implement CloudFront signer
- Cần manage private keys securely

#### **📝 Implementation Steps:**

##### **Step 1: Create CloudFront Key Pair**
```bash
# Generate RSA key pair
openssl genrsa -out private_key.pem 2048
openssl rsa -pubout -in private_key.pem -out public_key.pem

# Upload public key to CloudFront
# AWS Console → CloudFront → Key Management → Public Keys
```

##### **Step 2: Add CloudFront Signer Service**

```java
@Service
@RequiredArgsConstructor
public class CloudFrontSignerService {
    
    @Value("${cloudfront.domain}")
    private String cloudfrontDomain;
    
    @Value("${cloudfront.keypair-id}")
    private String keypairId;
    
    @Value("${cloudfront.private-key-path}")
    private String privateKeyPath;
    
    public String generateSignedUrl(String s3Key, Duration expiration) {
        try {
            // Load private key
            PrivateKey privateKey = loadPrivateKey(privateKeyPath);
            
            // Create CloudFront URL
            String resourceUrl = "https://" + cloudfrontDomain + "/" + s3Key;
            
            // Set expiration
            Instant expirationTime = Instant.now().plus(expiration);
            
            // Generate signed URL
            CloudFrontUrlSigner signer = CloudFrontUrlSigner.create(
                CloudFrontUrlSigner.SignerProvider.fromKeyPair(keypairId, privateKey)
            );
            
            return signer.getSignedURLWithCannedPolicy(
                resourceUrl,
                keypairId,
                privateKey,
                Date.from(expirationTime)
            );
            
        } catch (Exception e) {
            log.error("Error generating CloudFront signed URL: {}", e.getMessage());
            throw new AppException(ErrorCode.URL_GENERATION_FAILED);
        }
    }
    
    private PrivateKey loadPrivateKey(String path) throws Exception {
        // Load private key from file or secrets manager
        // Implementation details...
    }
}
```

##### **Step 3: Update PortfolioServiceImpl**

```java
private void convertS3KeysToUrls(PortfolioResponse response) {
    if (response.getCoverImageUrl() != null && !response.getCoverImageUrl().isEmpty()) {
        // Use CloudFront signed URL instead of S3 presigned URL
        String signedUrl = cloudFrontSignerService.generateSignedUrl(
                response.getCoverImageUrl(), 
                Duration.ofDays(7)); // 7 days expiration
        response.setCoverImageUrl(signedUrl);
    }
    // ... same for other images
}
```

#### **🎯 Khi nào dùng:**
- ✅ Production environment
- ✅ Khi cần longer expiration (> 24h)
- ✅ Khi cần advanced security (IP restrictions, custom policies)
- ✅ Khi đã có CloudFront setup

---

### **Cách 3: Public S3 + CloudFront (No Expiration) 🏆 (Best)**

**Mô tả:** Cho phép public read cho portfolio images, dùng CloudFront để serve với URL vĩnh viễn.

#### **✅ Ưu điểm:**
- **Không expire** - URL vĩnh viễn, không bao giờ lỗi
- Fastest loading (CloudFront caching)
- Đơn giản nhất cho frontend
- SEO friendly (URL không đổi)
- Không cần generate URLs mỗi lần request
- Có thể cache URLs ở frontend

#### **⚠️ Nhược điểm:**
- Ảnh public (nhưng portfolio images thường là public anyway)
- Cần config S3 bucket policy cẩn thận
- Không thể revoke access (ảnh public mãi mãi)

#### **📝 Implementation Steps:**

##### **Step 1: Config S3 Bucket Policy**

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadPortfolioImages",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": [
        "arn:aws:s3:::your-bucket-name/users/*/portfolio/*",
        "arn:aws:s3:::your-bucket-name/users/*/avatar/*"
      ]
    }
  ]
}
```

**⚠️ Important:** Chỉ cho phép public read cho portfolio và avatar, KHÔNG cho phép:
- Contract documents
- Private project files
- User sensitive data

##### **Step 2: Create CloudFront URL Generator Service**

```java
@Service
@RequiredArgsConstructor
public class PublicUrlService {
    
    @Value("${cloudfront.domain}")
    private String cloudfrontDomain;
    
    /**
     * Convert S3 key to public CloudFront URL (no expiration)
     */
    public String toPublicUrl(String s3Key) {
        if (s3Key == null || s3Key.isEmpty()) {
            return null;
        }
        
        // Check if already a URL
        if (s3Key.startsWith("http")) {
            return s3Key;
        }
        
        // Generate public CloudFront URL
        return "https://" + cloudfrontDomain + "/" + s3Key;
    }
    
    /**
     * Check if S3 key is for public content
     */
    public boolean isPublicContent(String s3Key) {
        return s3Key != null && (
            s3Key.startsWith("users/") && (
                s3Key.contains("/portfolio/") || 
                s3Key.contains("/avatar/")
            )
        );
    }
}
```

##### **Step 3: Update PortfolioServiceImpl**

```java
@Service
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {
    
    private final FileStorageService fileStorageService;
    private final PublicUrlService publicUrlService; // New
    
    private void convertS3KeysToUrls(PortfolioResponse response) {
        // Convert cover image - use public URL (no expiration)
        if (response.getCoverImageUrl() != null && !response.getCoverImageUrl().isEmpty()) {
            String publicUrl = publicUrlService.toPublicUrl(response.getCoverImageUrl());
            response.setCoverImageUrl(publicUrl);
            log.debug("Converted cover image to public URL: {}", publicUrl);
        }
        
        // Convert avatar - use public URL
        if (response.getAvatarUrl() != null && !response.getAvatarUrl().isEmpty() 
                && !response.getAvatarUrl().startsWith("http")) {
            String publicUrl = publicUrlService.toPublicUrl(response.getAvatarUrl());
            response.setAvatarUrl(publicUrl);
            log.debug("Converted avatar to public URL: {}", publicUrl);
        }
        
        // Convert personal project images - use public URL
        if (response.getPersonalProjects() != null) {
            response.getPersonalProjects().forEach(project -> {
                if (project.getCoverImageUrl() != null && !project.getCoverImageUrl().isEmpty()
                        && !project.getCoverImageUrl().startsWith("http")) {
                    String publicUrl = publicUrlService.toPublicUrl(project.getCoverImageUrl());
                    project.setCoverImageUrl(publicUrl);
                }
            });
        }
    }
}
```

##### **Step 4: Update application.yml**

```yaml
cloudfront:
  domain: d123abc456def.cloudfront.net  # Your CloudFront domain
```

#### **🎯 Khi nào dùng:**
- ✅ Production environment (RECOMMENDED)
- ✅ Khi portfolio images là public content
- ✅ Khi muốn best performance
- ✅ Khi muốn SEO-friendly URLs
- ✅ Khi muốn simplify frontend (no URL refresh logic)

---

## 📊 So sánh 3 cách

| Aspect | Cách 1: Long Presigned | Cách 2: CloudFront Signed | Cách 3: Public CloudFront |
|--------|----------------------|--------------------------|--------------------------|
| **Expiration** | 24h - 7 days | 7 days - custom | ❌ Never expires |
| **Performance** | Good | Better (CDN) | Best (CDN + no signing) |
| **Security** | Medium | High | Low (public) |
| **Complexity** | ⭐ Low | ⭐⭐⭐ High | ⭐⭐ Medium |
| **Frontend** | Simple | Simple | Simplest |
| **SEO** | ❌ Poor (URLs change) | ❌ Poor (URLs change) | ✅ Good (URLs stable) |
| **Cost** | Low | Medium | Low |
| **Setup Time** | 5 mins | 2 hours | 30 mins |

---

## 🎯 Khuyến nghị

### **Cho Development/MVP:**
→ **Cách 1** (Long Presigned URLs - 24h)
- Đã implement sẵn
- Đủ tốt cho testing
- Không cần config thêm

### **Cho Production:**
→ **Cách 3** (Public CloudFront URLs)
- Best performance
- No expiration issues
- SEO friendly
- Portfolio images thường là public anyway

### **Cho High Security Requirements:**
→ **Cách 2** (CloudFront Signed URLs)
- Nếu cần control access
- Nếu cần IP restrictions
- Nếu cần audit logs

---

## 🔧 Migration Path

```
Phase 1: Development (Current)
  └─ Cách 1: Presigned URLs (24h)
  
Phase 2: Beta Testing
  └─ Cách 1: Presigned URLs (7 days)
  
Phase 3: Production Launch
  └─ Cách 3: Public CloudFront URLs
  
Phase 4: Enterprise (if needed)
  └─ Cách 2: CloudFront Signed URLs
```

---

## 📚 References

- [AWS S3 Presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html)
- [CloudFront Signed URLs](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-signed-urls.html)
- [S3 Bucket Policies](https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucket-policies.html)

---

**Current Implementation: Cách 1 (24-hour presigned URLs) ✅**

