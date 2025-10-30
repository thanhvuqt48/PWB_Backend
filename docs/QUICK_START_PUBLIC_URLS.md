# 🚀 Quick Start: Public CloudFront URLs

## TL;DR

Portfolio và avatar images giờ dùng **public CloudFront URLs** - không bao giờ expire!

---

## ✅ What Changed

### **Before:**
```json
{
  "coverImageUrl": "https://cloudfront.net/...?X-Amz-Algorithm=...&X-Amz-Expires=86400"
}
```
- ⏰ Expires after 24 hours
- ⚠️ User sees error if page open too long

### **After:**
```json
{
  "coverImageUrl": "https://cloudfront.net/users/1/portfolio/cover/abc-123.jpg"
}
```
- ✅ Never expires
- ✅ Clean URL
- ✅ SEO friendly

---

## 🔧 Setup (3 Steps)

### **Step 1: Configure S3 Bucket Policy**

AWS Console → S3 → Your Bucket → Permissions → Bucket Policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": [
        "arn:aws:s3:::YOUR-BUCKET/users/*/portfolio/*",
        "arn:aws:s3:::YOUR-BUCKET/users/*/avatar/*"
      ]
    }
  ]
}
```

**Replace `YOUR-BUCKET` with your bucket name!**

---

### **Step 2: Disable Block Public Access**

AWS Console → S3 → Your Bucket → Permissions → Block Public Access → Edit → **Uncheck all**

---

### **Step 3: Set Environment Variables**

```bash
CLOUDFRONT_DOMAIN=d123abc456def.cloudfront.net
CLOUDFRONT_USE_PUBLIC_URLS=true
```

---

## ✅ Verify

Create portfolio and check response:

```bash
# Should see clean URL (no X-Amz-Algorithm)
"coverImageUrl": "https://cloudfront.net/users/1/portfolio/cover/abc-123.jpg"
```

---

## 🔒 Security

**Public (safe):**
- ✅ Portfolio images
- ✅ Avatars

**Private (still using presigned URLs):**
- 🔐 Contracts
- 🔐 Private project files

---

## 📚 Full Documentation

- **Setup Guide:** `docs/S3_PUBLIC_ACCESS_SETUP.md`
- **Implementation Details:** `docs/PUBLIC_CLOUDFRONT_URLS_IMPLEMENTATION.md`
- **All Solutions Comparison:** `docs/IMAGE_URL_EXPIRATION_SOLUTIONS.md`

---

## 🆘 Troubleshooting

**Still getting presigned URLs?**
→ Check `CLOUDFRONT_USE_PUBLIC_URLS=true`

**Getting 403 Forbidden?**
→ Check S3 bucket policy and Block Public Access

**Need help?**
→ See `docs/S3_PUBLIC_ACCESS_SETUP.md` (Troubleshooting section)

---

**Status: Ready to use after S3 setup! 🚀**

