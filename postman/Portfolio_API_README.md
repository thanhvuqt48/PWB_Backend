# Portfolio API - Image URL Flow

## 🔐 Giải thích cách xử lý Image URLs

### **❓ Vấn đề:**
Nếu DB chỉ lưu S3 key (ví dụ: `users/1/portfolio/cover/abc-123.jpg`), frontend không thể hiển thị ảnh vì đây không phải URL.

### **✅ Giải pháp: Public CloudFront URLs (No Expiration) 🏆**

**Current Implementation:** Sử dụng public CloudFront URLs cho portfolio/avatar images - URLs không bao giờ expire!

---

## 📦 1. Lưu trữ trong Database

**Lưu S3 Key (đường dẫn tương đối), KHÔNG lưu URL**

```sql
-- Portfolio table
coverImageUrl: "users/1/portfolio/cover/abc-123.jpg"  ✅ S3 Key
coverImageUrl: "https://cloudfront.../abc-123.jpg"    ❌ URL (KHÔNG nên)
```

**Lý do:**
- ✅ Presigned URLs có thời hạn (expire sau 1 giờ)
- ✅ Nếu đổi CloudFront domain, không cần update DB
- ✅ Tiết kiệm dung lượng DB
- ✅ Linh hoạt: có thể generate URL với expiration khác nhau

---

## 🔄 2. Convert trong Service Layer

**Backend tự động convert S3 key → Public CloudFront URL (hoặc Presigned URL nếu disabled)**

### Code Implementation:

Uses `PublicUrlService` which automatically chooses:
- **Public CloudFront URLs** (no expiration) when `cloudfront.use-public-urls=true`
- **Presigned URLs** (24h expiration) when `cloudfront.use-public-urls=false` or as fallback

<augment_code_snippet path="src/main/java/com/fpt/producerworkbench/service/impl/PortfolioServiceImpl.java" mode="EXCERPT">
```java
@Override
@Transactional
public PortfolioResponse create(PortfolioRequest request, MultipartFile coverImage) {
    // ... upload file, save to DB ...

    Portfolio savedPortfolio = portfolioRepository.save(portfolio);
    PortfolioResponse response = portfolioMapper.toPortfolioResponse(savedPortfolio);

    // ⭐ Convert tất cả S3 keys thành URLs (public CloudFront hoặc presigned)
    convertS3KeysToUrls(response);

    return response;
}

private void convertS3KeysToUrls(PortfolioResponse response) {
    // Convert cover image - uses PublicUrlService
    if (response.getCoverImageUrl() != null && !response.getCoverImageUrl().isEmpty()) {
        String url = publicUrlService.toUrl(response.getCoverImageUrl());
        response.setCoverImageUrl(url);
    }

    // Convert avatar - uses PublicUrlService
    if (response.getAvatarUrl() != null && !response.getAvatarUrl().startsWith("http")) {
        String url = publicUrlService.toUrl(response.getAvatarUrl());
        response.setAvatarUrl(url);
    }

    // Convert personal project images - uses PublicUrlService
    if (response.getPersonalProjects() != null) {
        response.getPersonalProjects().forEach(project -> {
            if (project.getCoverImageUrl() != null && !project.getCoverImageUrl().startsWith("http")) {
                String url = publicUrlService.toUrl(project.getCoverImageUrl());
                project.setCoverImageUrl(url);
            }
        });
    }
}
```
</augment_code_snippet>

---

## 📤 3. Response trả về cho Frontend

**API Response chứa Public CloudFront URLs (clean URLs, no expiration)**

```json
{
    "code": 201,
    "message": "Tạo portfolio thành công",
    "result": {
        "id": 2,
        "userId": 1,
        "firstName": "Pham",
        "lastName": "Thanh",
        "avatarUrl": "https://d123abc.cloudfront.net/users/1/avatar/profile.jpg",
        "coverImageUrl": "https://d123abc.cloudfront.net/users/1/portfolio/cover/dbf322ec-2c14-4343-a44b-bebteb1e9ee2.jpg",
        "personalProjects": [
            {
                "coverImageUrl": "https://d123abc.cloudfront.net/users/1/portfolio/projects/1/image.jpg"
            }
        ]
    }
}
```

**Note:** URLs are clean (no `X-Amz-Algorithm` parameters) and never expire!

---

## 🖼️ 4. Frontend hiển thị

**Frontend nhận presigned URL và dùng trực tiếp**

```jsx
// React example
<img src={portfolio.coverImageUrl} alt="Cover" />

// HTML
<img src="https://d123abc.cloudfront.net/users/1/portfolio/cover/abc-123.jpg?X-Amz-..." />
```

---

## 🔄 Flow hoàn chỉnh

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT (Frontend)                        │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ 1. POST /api/v1/portfolios
                             │    - coverImage: file.jpg
                             │    - data: {...}
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CONTROLLER (Backend)                          │
│  - Nhận MultipartFile + JSON data                               │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ 2. Call service.create()
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SERVICE LAYER                                 │
│  Step 1: Upload file to S3                                      │
│          ├─► S3: users/1/portfolio/cover/abc-123.jpg            │
│          └─► Return S3 key                                      │
│                                                                  │
│  Step 2: Save to Database                                       │
│          └─► DB: coverImageUrl = "users/1/portfolio/cover/..."  │
│                                                                  │
│  Step 3: Map entity → DTO                                       │
│          └─► PortfolioResponse (still has S3 keys)              │
│                                                                  │
│  Step 4: Convert S3 keys → Presigned URLs                       │
│          └─► response.setCoverImageUrl(presignedUrl)            │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ 3. Return response with URLs
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT (Frontend)                        │
│  - Receive presigned URLs                                       │
│  - Display images directly                                      │
│  - <img src="https://cloudfront.../abc-123.jpg?X-Amz-..." />   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📋 Các fields được convert

| Field | Location | Convert? |
|-------|----------|----------|
| `coverImageUrl` | Portfolio | ✅ Yes |
| `avatarUrl` | User | ✅ Yes |
| `personalProjects[].coverImageUrl` | Personal Project | ✅ Yes |
| `personalProjects[].audioDemoUrl` | Personal Project | ❌ No (external URL) |
| `socialLinks[].url` | Social Link | ❌ No (external URL) |

---

## ⚠️ Lưu ý quan trọng

### 1. **Public CloudFront URLs không expire**
- ✅ URLs **không bao giờ expire** - user có thể xem mãi mãi
- ✅ Frontend **không cần refresh** URLs
- ✅ Có thể cache URLs vĩnh viễn ở frontend/CDN
- ⚠️ Chỉ dùng cho public content (portfolio/avatar)

### 2. **Configuration Required**
```yaml
# application-dev.yml
cloudfront:
  domain: d123abc.cloudfront.net  # Required
  use-public-urls: true           # Enable public URLs
```

### 3. **S3 Bucket Policy Required**
- ⚠️ Cần config S3 bucket policy để allow public read
- 📄 Xem hướng dẫn chi tiết: `docs/S3_PUBLIC_ACCESS_SETUP.md`
- ✅ Chỉ cho phép public read cho `users/*/portfolio/*` và `users/*/avatar/*`
- ❌ KHÔNG cho phép public read cho contracts, private files

### 4. **Phân biệt S3 key vs External URL**
```java
// Check nếu là S3 key (không bắt đầu bằng http)
if (!url.startsWith("http")) {
    // Convert to public CloudFront URL
}
```

### 5. **CloudFront vs S3 Direct**
- ✅ **CloudFront**: Nhanh hơn, có caching, recommended
- ⚠️ **S3 Direct**: Chậm hơn, không cache

### 6. **Security**
- ⚠️ Portfolio/avatar images là **public** (anyone can access)
- ✅ Contract documents vẫn dùng **presigned URLs** (private)
- ✅ Private project files vẫn dùng **presigned URLs** (private)

---

## 🧪 Testing với Postman

### Test Case: Verify Image URLs

```javascript
// Postman Test Script
pm.test("Cover image URL is presigned URL", function () {
    var jsonData = pm.response.json();
    var coverImageUrl = jsonData.result.coverImageUrl;
    
    // Check if it's a full URL (not just S3 key)
    pm.expect(coverImageUrl).to.include("https://");
    
    // Check if it has AWS signature
    pm.expect(coverImageUrl).to.include("X-Amz-");
    
    console.log("Cover Image URL: " + coverImageUrl);
});

pm.test("Avatar URL is presigned URL", function () {
    var jsonData = pm.response.json();
    var avatarUrl = jsonData.result.avatarUrl;
    
    if (avatarUrl) {
        pm.expect(avatarUrl).to.include("https://");
        console.log("Avatar URL: " + avatarUrl);
    }
});
```

---

## 🔧 Troubleshooting

### **Lỗi: Frontend không hiển thị được ảnh**

**Nguyên nhân 1**: Response trả về S3 key thay vì URL
```json
// ❌ Wrong
"coverImageUrl": "users/1/portfolio/cover/abc-123.jpg"

// ✅ Correct
"coverImageUrl": "https://d123abc.cloudfront.net/users/1/portfolio/cover/abc-123.jpg?X-Amz-..."
```
**Giải pháp**: Kiểm tra `convertS3KeysToUrls()` có được gọi không

---

**Nguyên nhân 2**: Presigned URL đã hết hạn
```
Error: 403 Forbidden
```
**Giải pháp**: Frontend gọi lại API để lấy URL mới

---

**Nguyên nhân 3**: CORS issue
```
Access to image blocked by CORS policy
```
**Giải pháp**: Config CORS cho S3 bucket hoặc CloudFront

---

## 📚 Best Practices

1. ✅ **Luôn lưu S3 key trong DB**, không lưu URL
2. ✅ **Convert sang URL ở service layer**, không ở controller
3. ✅ **Check null và empty** trước khi convert
4. ✅ **Phân biệt S3 key vs external URL** (check `startsWith("http")`)
5. ✅ **Log presigned URLs** để debug (nhưng không log vào production)
6. ✅ **Set expiration time hợp lý** (1 giờ cho view, 5 phút cho upload)

---

## 🎯 Summary

| Aspect | Solution |
|--------|----------|
| **Storage** | Lưu S3 key trong DB |
| **Processing** | Convert S3 key → Presigned URL trong service |
| **Response** | Trả về presigned URL cho frontend |
| **Display** | Frontend dùng URL trực tiếp |
| **Expiration** | URL expire sau 1 giờ, frontend refresh khi cần |

---

**Happy Coding! 🚀**

