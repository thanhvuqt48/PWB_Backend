# 🔧 S3 Public Access Setup for Portfolio Images

## 📋 Overview

This guide explains how to configure S3 bucket policy to allow public read access for portfolio and avatar images, enabling permanent CloudFront URLs without expiration.

---

## ⚠️ Important Security Notes

**ONLY make portfolio and avatar images public. DO NOT make these public:**
- ❌ Contract documents (`contracts/**`)
- ❌ Private project files (`projects/**`)
- ❌ Milestone deliverables (`projects/*/milestones/**`)
- ❌ Any sensitive user data

**Public content (safe to make public):**
- ✅ Portfolio cover images (`users/*/portfolio/cover/**`)
- ✅ Personal project images (`users/*/portfolio/projects/**`)
- ✅ User avatars (`users/*/avatar/**`)

---

## 🚀 Setup Steps

### **Step 1: Configure S3 Bucket Policy**

#### **Option A: AWS Console (Recommended for beginners)**

1. Go to [AWS S3 Console](https://s3.console.aws.amazon.com/)
2. Click on your bucket (e.g., `pwb-storage-bucket`)
3. Go to **Permissions** tab
4. Scroll down to **Bucket policy**
5. Click **Edit**
6. Paste the following policy:

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
        "arn:aws:s3:::YOUR-BUCKET-NAME/users/*/portfolio/*",
        "arn:aws:s3:::YOUR-BUCKET-NAME/users/*/avatar/*"
      ]
    }
  ]
}
```

7. **Replace `YOUR-BUCKET-NAME`** with your actual bucket name
8. Click **Save changes**

#### **Option B: AWS CLI**

```bash
# Create policy file
cat > s3-public-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadPortfolioImages",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": [
        "arn:aws:s3:::YOUR-BUCKET-NAME/users/*/portfolio/*",
        "arn:aws:s3:::YOUR-BUCKET-NAME/users/*/avatar/*"
      ]
    }
  ]
}
EOF

# Replace YOUR-BUCKET-NAME
sed -i 's/YOUR-BUCKET-NAME/pwb-storage-bucket/g' s3-public-policy.json

# Apply policy
aws s3api put-bucket-policy \
  --bucket pwb-storage-bucket \
  --policy file://s3-public-policy.json
```

---

### **Step 2: Disable "Block Public Access" for the bucket**

By default, S3 blocks all public access. You need to allow public access for this bucket.

#### **AWS Console:**

1. In S3 Console, go to your bucket
2. Go to **Permissions** tab
3. Click **Edit** under **Block public access (bucket settings)**
4. **Uncheck** these options:
   - ❌ Block all public access
   - ❌ Block public access to buckets and objects granted through new access control lists (ACLs)
   - ❌ Block public access to buckets and objects granted through any access control lists (ACLs)
   - ❌ Block public access to buckets and objects granted through new public bucket or access point policies
   - ❌ Block public and cross-account access to buckets and objects through any public bucket or access point policies
5. Click **Save changes**
6. Type `confirm` and click **Confirm**

#### **AWS CLI:**

```bash
aws s3api put-public-access-block \
  --bucket pwb-storage-bucket \
  --public-access-block-configuration \
    "BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false"
```

---

### **Step 3: Configure CloudFront (if using)**

If you're using CloudFront, you need to allow CloudFront to access S3 public content.

#### **Option A: CloudFront with S3 Origin (Recommended)**

1. Go to [CloudFront Console](https://console.aws.amazon.com/cloudfront/)
2. Click on your distribution
3. Go to **Origins** tab
4. Edit your S3 origin
5. Set **Origin access** to:
   - **Public** (for public content)
   - Or keep **Origin Access Control (OAC)** if you want CloudFront to control access
6. Save changes

#### **Option B: Update CloudFront Origin Access Identity (OAI)**

If using OAI, update S3 bucket policy to allow CloudFront:

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
        "arn:aws:s3:::YOUR-BUCKET-NAME/users/*/portfolio/*",
        "arn:aws:s3:::YOUR-BUCKET-NAME/users/*/avatar/*"
      ]
    },
    {
      "Sid": "CloudFrontReadAll",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::cloudfront:user/CloudFront Origin Access Identity YOUR-OAI-ID"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::YOUR-BUCKET-NAME/*"
    }
  ]
}
```

Replace:
- `YOUR-BUCKET-NAME` with your bucket name
- `YOUR-OAI-ID` with your CloudFront OAI ID

---

### **Step 4: Update Application Configuration**

Update your `.env` or environment variables:

```bash
# CloudFront domain (required)
CLOUDFRONT_DOMAIN=d123abc456def.cloudfront.net

# Enable public URLs (set to true for production)
CLOUDFRONT_USE_PUBLIC_URLS=true
```

Or in `application-dev.yml`:

```yaml
cloudfront:
  domain: d123abc456def.cloudfront.net
  use-public-urls: true  # Enable permanent URLs
```

---

## ✅ Verification

### **Test 1: Verify S3 Public Access**

Upload a test image and try to access it directly:

```bash
# Upload test image
aws s3 cp test.jpg s3://pwb-storage-bucket/users/1/portfolio/cover/test.jpg

# Try to access directly (should work)
curl -I https://pwb-storage-bucket.s3.ap-southeast-1.amazonaws.com/users/1/portfolio/cover/test.jpg

# Expected: HTTP 200 OK
```

### **Test 2: Verify CloudFront Access**

```bash
# Try to access via CloudFront (should work)
curl -I https://d123abc456def.cloudfront.net/users/1/portfolio/cover/test.jpg

# Expected: HTTP 200 OK
```

### **Test 3: Verify Private Content is Still Private**

```bash
# Try to access contract (should fail)
curl -I https://pwb-storage-bucket.s3.ap-southeast-1.amazonaws.com/contracts/1/signed_v1.pdf

# Expected: HTTP 403 Forbidden (good!)
```

### **Test 4: Test from Application**

Create a portfolio with cover image and check the response:

```bash
# Create portfolio
curl -X POST http://localhost:8080/api/v1/portfolios \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -F "coverImage=@test.jpg" \
  -F 'data={"customUrlSlug":"test","headline":"Test",...}'

# Check response - coverImageUrl should be:
# https://d123abc456def.cloudfront.net/users/1/portfolio/cover/abc-123.jpg
# (NOT a presigned URL with X-Amz-Algorithm parameters)
```

---

## 🔧 Troubleshooting

### **Problem 1: Still getting 403 Forbidden**

**Cause:** Block Public Access is still enabled

**Solution:**
```bash
# Check current settings
aws s3api get-public-access-block --bucket pwb-storage-bucket

# Disable all blocks
aws s3api put-public-access-block \
  --bucket pwb-storage-bucket \
  --public-access-block-configuration \
    "BlockPublicAcls=false,IgnorePublicAcls=false,BlockPublicPolicy=false,RestrictPublicBuckets=false"
```

---

### **Problem 2: Application still returns presigned URLs**

**Cause:** `use-public-urls` is set to `false` or CloudFront domain not configured

**Solution:**
```bash
# Check environment variables
echo $CLOUDFRONT_DOMAIN
echo $CLOUDFRONT_USE_PUBLIC_URLS

# Should output:
# d123abc456def.cloudfront.net
# true

# If not set, add to .env:
CLOUDFRONT_DOMAIN=d123abc456def.cloudfront.net
CLOUDFRONT_USE_PUBLIC_URLS=true
```

---

### **Problem 3: CloudFront returns 403 but S3 direct access works**

**Cause:** CloudFront origin not configured correctly

**Solution:**
1. Go to CloudFront Console
2. Edit your distribution
3. Go to Origins tab
4. Edit S3 origin
5. Change **Origin access** to **Public**
6. Save and wait for deployment (5-10 minutes)

---

### **Problem 4: Old images still use presigned URLs**

**Cause:** URLs are generated at request time, not stored

**Solution:** This is expected! Old portfolio responses will automatically use new URL format on next API call. No database migration needed.

---

## 📊 Comparison: Before vs After

### **Before (Presigned URLs):**

```json
{
  "coverImageUrl": "https://d123abc.cloudfront.net/users/1/portfolio/cover/abc-123.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=...&X-Amz-Expires=86400&..."
}
```

- ⏰ Expires after 24 hours
- 🔐 Has signature parameters
- 📏 Very long URL

### **After (Public CloudFront URLs):**

```json
{
  "coverImageUrl": "https://d123abc.cloudfront.net/users/1/portfolio/cover/abc-123.jpg"
}
```

- ✅ Never expires
- 🚀 Clean, short URL
- 🔍 SEO friendly

---

## 🔒 Security Best Practices

### **✅ DO:**
- Only make portfolio and avatar images public
- Use presigned URLs for sensitive content (contracts, private files)
- Monitor S3 access logs for suspicious activity
- Use CloudFront for better performance and DDoS protection
- Set up CloudWatch alarms for unusual traffic

### **❌ DON'T:**
- Don't make entire bucket public
- Don't make contract documents public
- Don't make private project files public
- Don't disable encryption at rest
- Don't forget to monitor costs (CloudFront + S3 egress)

---

## 💰 Cost Considerations

### **S3 Costs:**
- **Storage:** ~$0.023/GB/month (first 50 TB)
- **GET requests:** $0.0004 per 1,000 requests
- **Data transfer out:** $0.09/GB (first 10 TB)

### **CloudFront Costs:**
- **Data transfer out:** $0.085/GB (first 10 TB) - cheaper than S3 direct
- **HTTP requests:** $0.0075 per 10,000 requests
- **HTTPS requests:** $0.01 per 10,000 requests

### **Savings with CloudFront:**
- ✅ Cheaper data transfer ($0.085 vs $0.09 per GB)
- ✅ Caching reduces S3 GET requests
- ✅ Better performance for users

---

## 📚 References

- [AWS S3 Bucket Policies](https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucket-policies.html)
- [AWS S3 Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html)
- [CloudFront with S3 Origins](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/DownloadDistS3AndCustomOrigins.html)

---

## ✅ Checklist

Before going to production, make sure:

- [ ] S3 bucket policy configured for public read on portfolio/avatar paths
- [ ] Block Public Access disabled for the bucket
- [ ] CloudFront distribution configured with S3 origin
- [ ] Environment variables set (`CLOUDFRONT_DOMAIN`, `CLOUDFRONT_USE_PUBLIC_URLS=true`)
- [ ] Tested public access for portfolio images
- [ ] Verified private content (contracts) is still private
- [ ] Monitored S3/CloudFront costs
- [ ] Set up CloudWatch alarms for unusual traffic

---

**Status: Ready for Production ✅**

