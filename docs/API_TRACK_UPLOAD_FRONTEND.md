# API Documentation - Track Upload cho Frontend

> **Document Version:** 1.0  
> **Last Updated:** November 16, 2024  
> **Audience:** Frontend Developers  
> **Purpose:** Hướng dẫn gọi API để implement chức năng upload và quản lý track

---

## 📋 Mục Lục

1. [Tổng Quan Flow](#tổng-quan-flow)
2. [Authentication](#authentication)
3. [API Endpoints](#api-endpoints)
4. [Upload Flow Step-by-Step](#upload-flow-step-by-step)
5. [Status Polling](#status-polling)
6. [Error Handling](#error-handling)
7. [UI/UX Recommendations](#uiux-recommendations)

---

## 🎯 Tổng Quan Flow

### High-Level Steps

```
1. User chọn file và nhập thông tin track
   ↓
2. FE gọi API tạo track → nhận uploadUrl
   ↓
3. FE upload file trực tiếp lên S3 qua uploadUrl (PUT request)
   ↓
4. FE gọi API finalize để trigger xử lý
   ↓
5. FE poll status cho đến khi track READY
   ↓
6. FE hiển thị player với playbackUrl
```

### Timeline Estimates

| Phase | Duration | Note |
|-------|----------|------|
| Step 2: Create track | < 1s | API call nhanh |
| Step 3: Upload file | 10s - 5min | Tùy file size và network |
| Step 4: Finalize | < 1s | API call nhanh |
| Step 5: Processing | 30s - 3min | Background processing |
| **Total** | **1min - 8min** | Thường ~2-3 phút cho track 5MB |

---

## 🔐 Authentication

### Required Header

Tất cả API calls (trừ S3 upload) cần header:

```
Authorization: Bearer {access_token}
```

### Getting Access Token

- User phải đăng nhập trước
- Access token có từ login response
- Token expires sau 24 giờ (refresh nếu cần)

---

## 🔌 API Endpoints

### Base URL

```
Production: https://api.producer-workbench.com/api/v1
Development: http://localhost:8080/api/v1
```

---

## 📡 1. Create Track

### Endpoint

```
POST /projects/{projectId}/milestones/{milestoneId}/tracks
```

### Purpose

- Tạo track entity trong hệ thống
- Nhận presigned URL để upload file lên S3
- Set up voice tag configuration

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `projectId` | Long | ✅ | ID của project |
| `milestoneId` | Long | ✅ | ID của milestone chứa track |

### Request Headers

```
Authorization: Bearer {access_token}
Content-Type: application/json
```

### Request Body

```json
{
  "name": "Beat Lofi Chill #1",
  "description": "Beat lofi cho project X, version đầu tiên",
  "version": "v1",
  "contentType": "audio/wav",
  "fileSize": 52428800,
  "voiceTagEnabled": true,
  "voiceTagText": "Demo thuộc về Producer X, chỉ để nghe trước"
}
```

#### Request Fields

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| `name` | String | ✅ | Tên track | "Beat Lofi Chill #1" |
| `description` | String | ❌ | Mô tả track | "Beat lofi cho project X" |
| `version` | String | ✅ | Version của track | "v1", "v2", "final" |
| `contentType` | String | ✅ | MIME type của file | "audio/wav", "audio/mpeg", "audio/flac" |
| `fileSize` | Long | ✅ | Kích thước file (bytes) | 52428800 (50MB) |
| `voiceTagEnabled` | Boolean | ✅ | Có dùng voice tag không | true / false |
| `voiceTagText` | String | Conditional* | Nội dung voice tag | "Demo thuộc về Producer X" |

**Conditional:* `voiceTagText` bắt buộc nếu `voiceTagEnabled = true`

#### Supported Audio Formats

| Format | Content-Type | Extension | Recommended |
|--------|--------------|-----------|-------------|
| WAV | `audio/wav` | `.wav` | ✅ Best quality |
| MP3 | `audio/mpeg` | `.mp3` | ✅ Balanced |
| FLAC | `audio/flac` | `.flac` | ✅ Lossless |
| M4A | `audio/mp4` | `.m4a` | ✅ Good |
| AAC | `audio/aac` | `.aac` | ⚠️ OK |
| OGG | `audio/ogg` | `.ogg` | ⚠️ OK |

### Response - Success (201 Created)

```json
{
  "code": 201,
  "message": "Đã tạo track thành công. Vui lòng upload file master.",
  "result": {
    "trackId": 123,
    "uploadUrl": "https://pwb-bucket.s3.ap-southeast-1.amazonaws.com/audio/original/123/master.wav?X-Amz-Algorithm=...",
    "s3Key": "audio/original/123/master.wav",
    "expiresIn": 900
  }
}
```

#### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `trackId` | Long | ID của track vừa tạo (dùng cho các bước tiếp theo) |
| `uploadUrl` | String | Presigned URL để PUT file lên S3 |
| `s3Key` | String | S3 key của file (info only) |
| `expiresIn` | Long | Thời gian expire của uploadUrl (seconds) |

**⚠️ Important:**
- `uploadUrl` chỉ valid trong **15 phút** (900 seconds)
- Phải upload file trong khoảng thời gian này
- Sau khi expire, phải tạo track mới

### Possible Error Responses

**403 Forbidden - Access Denied:**
```json
{
  "code": 1002,
  "message": "Chỉ Owner hoặc COLLABORATOR mới có thể upload track"
}
```
hoặc
```json
{
  "code": 1002,
  "message": "Bạn cần chấp nhận phân chia tiền (Money Split) trước khi upload track"
}
```

**400 Bad Request - Validation Error:**
```json
{
  "code": 1003,
  "message": "Voice tag text không được để trống khi bật voice tag"
}
```
hoặc
```json
{
  "code": 1003,
  "message": "Milestone không thuộc project này"
}
```

**404 Not Found:**
```json
{
  "code": 1009,
  "message": "Milestone không tồn tại"
}
```

---

## 📤 2. Upload File to S3

### Endpoint

```
PUT {uploadUrl}
```

**Note:** Đây là S3 presigned URL, **KHÔNG** phải backend API

### Purpose

- Upload file audio trực tiếp lên S3
- Không qua backend server
- Support progress tracking

### Request Headers

```
Content-Type: {contentType từ request ban đầu}
```

**⚠️ Important:**
- **KHÔNG** gửi `Authorization` header cho S3
- `Content-Type` phải match với `contentType` đã khai báo khi tạo track
- Request body là **binary data** của file

### Request Body

- Binary content của audio file
- Không wrap trong JSON
- Không encode base64

### Example Using Fetch API

```javascript
// Giả sử:
// - file: File object từ input[type="file"]
// - uploadUrl: từ response của API Create Track
// - contentType: từ request Create Track

const response = await fetch(uploadUrl, {
  method: 'PUT',
  headers: {
    'Content-Type': contentType
  },
  body: file
});

if (response.ok) {
  console.log('Upload thành công');
  // Proceed to finalize
} else {
  console.error('Upload failed:', response.status);
}
```

### Response - Success (200 OK)

- S3 trả về status 200
- Response body có thể empty hoặc chứa XML metadata (không quan trọng)
- Quan trọng: Check `response.ok` hoặc `response.status === 200`

### Response - Error

| Status | Cause | Action |
|--------|-------|--------|
| 403 | Presigned URL expired hoặc sai | Tạo track mới |
| 400 | Content-Type không đúng | Kiểm tra lại Content-Type header |
| 413 | File quá lớn | S3 limit 5GB cho single PUT |
| 500 | S3 internal error | Retry sau vài giây |

### Progress Tracking

```javascript
// Sử dụng XMLHttpRequest để track progress
const xhr = new XMLHttpRequest();

xhr.upload.addEventListener('progress', (event) => {
  if (event.lengthComputable) {
    const percentComplete = (event.loaded / event.total) * 100;
    console.log(`Upload progress: ${percentComplete}%`);
    // Update progress bar UI
  }
});

xhr.addEventListener('load', () => {
  if (xhr.status === 200) {
    console.log('Upload complete');
    // Proceed to finalize
  }
});

xhr.addEventListener('error', () => {
  console.error('Upload failed');
});

xhr.open('PUT', uploadUrl);
xhr.setRequestHeader('Content-Type', contentType);
xhr.send(file);
```

---

## ✅ 3. Finalize Upload

### Endpoint

```
POST /tracks/{trackId}/finalize
```

### Purpose

- Thông báo cho backend: upload đã hoàn tất
- Trigger audio processing (TTS, mixing, HLS conversion)
- Bắt đầu chuyển trạng thái từ UPLOADING → PROCESSING

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `trackId` | Long | ✅ | ID của track (từ Create Track response) |

### Request Headers

```
Authorization: Bearer {access_token}
```

### Request Body

**Không có body** (empty)

### Response - Success (202 Accepted)

```json
{
  "code": 202,
  "message": "Đã bắt đầu xử lý audio cho track. Vui lòng đợi."
}
```

**Note:** Status 202 có nghĩa là request được accept, xử lý sẽ diễn ra async

### Possible Error Responses

**400 Bad Request:**
```json
{
  "code": 1003,
  "message": "Track không ở trạng thái UPLOADING"
}
```

**403 Forbidden:**
```json
{
  "code": 1002,
  "message": "Bạn không có quyền thao tác track này"
}
```

---

## 🔍 4. Get Track Details

### Endpoint

```
GET /tracks/{trackId}
```

### Purpose

- Lấy thông tin chi tiết của track
- Check processing status
- Lấy playback URL khi track READY

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `trackId` | Long | ✅ | ID của track |

### Request Headers

```
Authorization: Bearer {access_token}
```

### Response - Success (200 OK)

```json
{
  "code": 200,
  "message": "Lấy thông tin track thành công",
  "result": {
    "id": 123,
    "name": "Beat Lofi Chill #1",
    "description": "Beat lofi cho project X, version đầu tiên",
    "version": "v1",
    "milestoneId": 45,
    "userId": 10,
    "userName": "Nguyen Van A",
    "voiceTagEnabled": true,
    "voiceTagText": "Demo thuộc về Producer X, chỉ để nghe trước",
    "status": "INTERNAL_DRAFT",
    "processingStatus": "READY",
    "errorMessage": null,
    "contentType": "audio/wav",
    "fileSize": 52428800,
    "duration": 245,
    "hlsPlaybackUrl": "https://d123456789.cloudfront.net/audio/hls/123/index.m3u8",
    "createdAt": "2024-11-16T10:30:00Z",
    "updatedAt": "2024-11-16T10:33:25Z"
  }
}
```

#### Response Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `id` | Long | Track ID | 123 |
| `name` | String | Tên track | "Beat Lofi Chill #1" |
| `description` | String | Mô tả | "Beat lofi cho project X" |
| `version` | String | Version | "v1" |
| `milestoneId` | Long | ID milestone chứa track | 45 |
| `userId` | Long | ID người tạo | 10 |
| `userName` | String | Tên người tạo | "Nguyen Van A" |
| `voiceTagEnabled` | Boolean | Voice tag enabled | true / false |
| `voiceTagText` | String | Voice tag text | "Demo thuộc về..." |
| `status` | String | Business status | "INTERNAL_DRAFT", "INTERNAL_APPROVED", "INTERNAL_REJECTED" |
| `processingStatus` | String | Technical status | "UPLOADING", "PROCESSING", "READY", "FAILED" |
| `errorMessage` | String / null | Error nếu FAILED | null hoặc error detail |
| `contentType` | String | MIME type | "audio/wav" |
| `fileSize` | Long | File size (bytes) | 52428800 |
| `duration` | Integer / null | Duration (seconds) | 245 (= 4min 5s) |
| `hlsPlaybackUrl` | String / null | CloudFront streaming URL | "https://..." hoặc null |
| `createdAt` | String (ISO 8601) | Thời gian tạo | "2024-11-16T10:30:00Z" |
| `updatedAt` | String (ISO 8601) | Thời gian update | "2024-11-16T10:33:25Z" |

#### Processing Status Values

| Status | Meaning | UI Action |
|--------|---------|-----------|
| `UPLOADING` | Chờ user upload file | Show "Đang chờ upload..." |
| `PROCESSING` | Đang xử lý (TTS, HLS) | Show spinner "Đang xử lý audio..." |
| `READY` | Đã sẵn sàng phát | Show player with `hlsPlaybackUrl` |
| `FAILED` | Xử lý lỗi | Show error message from `errorMessage` |

#### Business Status Values

| Status | Meaning |
|--------|---------|
| `INTERNAL_DRAFT` | Draft, chưa review |
| `INTERNAL_APPROVED` | Đã duyệt nội bộ |
| `INTERNAL_REJECTED` | Bị reject nội bộ |

**Note:** Business status độc lập với processing status

---

## 📋 5. Get Tracks List

### Endpoint

```
GET /milestones/{milestoneId}/tracks
```

### Purpose

- Lấy danh sách tất cả tracks trong milestone
- Hiển thị trong list view

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `milestoneId` | Long | ✅ | ID của milestone |

### Request Headers

```
Authorization: Bearer {access_token}
```

### Response - Success (200 OK)

```json
{
  "code": 200,
  "message": "Lấy danh sách tracks thành công",
  "result": [
    {
      "id": 123,
      "name": "Beat Lofi Chill #1",
      "version": "v1",
      "processingStatus": "READY",
      "status": "INTERNAL_DRAFT",
      "duration": 245,
      "userName": "Nguyen Van A",
      "createdAt": "2024-11-16T10:30:00Z",
      "hlsPlaybackUrl": "https://..."
    },
    {
      "id": 124,
      "name": "Beat Lofi Chill #2",
      "version": "v2",
      "processingStatus": "PROCESSING",
      "status": "INTERNAL_DRAFT",
      "duration": null,
      "userName": "Tran Thi B",
      "createdAt": "2024-11-16T11:00:00Z",
      "hlsPlaybackUrl": null
    }
  ]
}
```

**Note:** Response là array các track objects (format giống Get Track Details)

---

## 🎵 6. Get Playback URL

### Endpoint

```
GET /tracks/{trackId}/playback-url
```

### Purpose

- Lấy CloudFront streaming URL để phát track
- URL này có thể dùng với HLS player

**Note:** Thực tế `hlsPlaybackUrl` đã có trong response của Get Track Details, nên endpoint này optional

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `trackId` | Long | ✅ | ID của track |

### Request Headers

```
Authorization: Bearer {access_token}
```

### Response - Success (200 OK)

```json
{
  "code": 200,
  "message": "Lấy playback URL thành công",
  "result": "https://d123456789.cloudfront.net/audio/hls/123/index.m3u8"
}
```

### Possible Error Responses

**400 Bad Request:**
```json
{
  "code": 1003,
  "message": "Track chưa sẵn sàng để phát. Trạng thái: PROCESSING"
}
```
hoặc
```json
{
  "code": 1003,
  "message": "HLS URL không tồn tại"
}
```

**403 Forbidden:**
```json
{
  "code": 1002,
  "message": "Chỉ Owner hoặc COLLABORATOR mới có thể xem track"
}
```
hoặc
```json
{
  "code": 1002,
  "message": "Bạn cần chấp nhận phân chia tiền (Money Split) trước khi xem track"
}
```

**404 Not Found:**
```json
{
  "code": 1009,
  "message": "Track không tồn tại"
}
```

---

## 🔄 Upload Flow Step-by-Step

### Complete Flow Example

```javascript
// ============================================
// STEP 1: Tạo Track
// ============================================
const createTrackRequest = {
  name: trackName,
  description: trackDescription,
  version: trackVersion,
  contentType: file.type, // "audio/wav"
  fileSize: file.size,
  voiceTagEnabled: true,
  voiceTagText: voiceTagText
};

const createResponse = await fetch(
  `${API_BASE_URL}/projects/${projectId}/milestones/${milestoneId}/tracks`,
  {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(createTrackRequest)
  }
);

const createData = await createResponse.json();
const { trackId, uploadUrl, expiresIn } = createData.result;

// ============================================
// STEP 2: Upload File to S3
// ============================================
const uploadResponse = await fetch(uploadUrl, {
  method: 'PUT',
  headers: {
    'Content-Type': file.type
  },
  body: file
});

if (!uploadResponse.ok) {
  throw new Error('Upload failed');
}

// ============================================
// STEP 3: Finalize Upload
// ============================================
const finalizeResponse = await fetch(
  `${API_BASE_URL}/tracks/${trackId}/finalize`,
  {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`
    }
  }
);

const finalizeData = await finalizeResponse.json();
console.log(finalizeData.message); // "Đã bắt đầu xử lý audio..."

// ============================================
// STEP 4: Poll Status
// ============================================
const pollInterval = setInterval(async () => {
  const statusResponse = await fetch(
    `${API_BASE_URL}/tracks/${trackId}`,
    {
      headers: {
        'Authorization': `Bearer ${accessToken}`
      }
    }
  );
  
  const statusData = await statusResponse.json();
  const { processingStatus, hlsPlaybackUrl, errorMessage } = statusData.result;
  
  if (processingStatus === 'READY') {
    clearInterval(pollInterval);
    console.log('Track ready!', hlsPlaybackUrl);
    // Load player with hlsPlaybackUrl
    
  } else if (processingStatus === 'FAILED') {
    clearInterval(pollInterval);
    console.error('Processing failed:', errorMessage);
    // Show error to user
    
  } else {
    console.log('Still processing...', processingStatus);
    // Update progress UI
  }
}, 5000); // Poll mỗi 5 giây
```

---

## 📊 Status Polling

### Strategy

**Polling** là cách đơn giản và reliable để check processing status

#### Recommended Approach

1. **Interval:** Poll mỗi **5 seconds**
2. **Timeout:** Stop sau **5 minutes** (processing thường < 3 phút)
3. **Stop Conditions:**
   - `processingStatus === 'READY'` → Success, hiển thị player
   - `processingStatus === 'FAILED'` → Error, hiển thị error message
   - Timeout → Show "Processing taking longer than expected" message

#### Alternative: WebSocket (Advanced)

- Nếu backend support WebSocket, có thể subscribe real-time updates
- Giảm số requests, UX tốt hơn
- Document riêng cho WebSocket integration (nếu có)

---

## ⚠️ Error Handling

### HTTP Status Codes

| Status | Meaning | Common Causes |
|--------|---------|---------------|
| 200 | OK | Request thành công |
| 201 | Created | Track created successfully |
| 202 | Accepted | Processing started (async) |
| 400 | Bad Request | Validation error, invalid params |
| 401 | Unauthorized | Token missing hoặc expired |
| 403 | Forbidden | Không có quyền (permission denied) |
| 404 | Not Found | Track/Milestone không tồn tại |
| 500 | Internal Server Error | Backend error |

### Error Response Format

```json
{
  "code": 1002,
  "message": "Bạn cần chấp nhận phân chia tiền (Money Split) trước khi upload track"
}
```

### Common Error Codes & Messages

| Code | HTTP Status | Example Message |
|------|-------------|-----------------|
| 1001 | 401 | "Token không hợp lệ hoặc đã hết hạn" |
| 1002 | 403 | "Chỉ Owner hoặc COLLABORATOR mới có thể upload track" |
| 1002 | 403 | "Bạn cần chấp nhận phân chia tiền (Money Split) trước khi upload track" |
| 1003 | 400 | "Voice tag text không được để trống khi bật voice tag" |
| 1003 | 400 | "Track không ở trạng thái UPLOADING" |
| 1009 | 404 | "Milestone không tồn tại" |
| 1009 | 404 | "Track không tồn tại" |
| 9999 | 500 | "Lỗi hệ thống. Vui lòng thử lại sau" |

### Error Handling Strategy

```javascript
try {
  const response = await fetch(url, options);
  const data = await response.json();
  
  if (!response.ok) {
    // Handle HTTP error
    switch (response.status) {
      case 401:
        // Token expired, redirect to login
        redirectToLogin();
        break;
        
      case 403:
        // Permission denied
        showErrorModal(data.message);
        break;
        
      case 400:
        // Validation error
        showValidationErrors(data.message);
        break;
        
      default:
        // Generic error
        showErrorToast(data.message || 'Đã có lỗi xảy ra');
    }
    return;
  }
  
  // Success handling
  handleSuccess(data);
  
} catch (error) {
  // Network error
  showErrorToast('Không thể kết nối server. Vui lòng kiểm tra internet.');
}
```

## 📝 Notes & Best Practices

### 1. File Size Validation

- **Frontend validation:** Check file size trước khi gọi API
- **Max size:** 1GB (server limit: `spring.servlet.multipart.max-file-size`)
- **Recommended:** 5MB - 100MB cho tracks thông thường

### 2. File Format Validation

- **Check extension:** `.wav`, `.mp3`, `.flac`, `.m4a`
- **Check MIME type:** Match với supported content types
- **Recommended:** WAV hoặc FLAC cho quality, MP3 cho size

### 3. Progress Indication

- **Upload progress:** Show % và estimated time
- **Processing progress:** Show spinner + current step
- **Timeout handling:** Nếu quá 5 phút, show message "Contact support"

### 4. Error Recovery

- **Network error during upload:** Cho phép retry
- **Processing failed:** Show error detail + retry button
- **Permission denied:** Hướng dẫn user approve Money Split

### 5. Voice Tag

- **Default text suggestion:** "Demo thuộc về [Producer Name], chỉ để nghe trước"
- **Character limit:** Recommend 50-100 characters (ngắn gọn, nghe không quá lâu)
- **Preview option:** Có thể thêm "Preview voice tag" button (gọi TTS trước để user nghe)

### 6. Concurrent Uploads

- **Limit:** Recommend upload tối đa 3 tracks đồng thời
- **Queue system:** Nếu user chọn nhiều files, queue và upload tuần tự
- **Avoid:** Upload quá nhiều files cùng lúc → overload browser + network

### 7. Polling Optimization

- **Start interval:** 5 seconds
- **Backoff strategy:** Tăng lên 10s sau 1 phút, 20s sau 3 phút
- **Stop condition:** Always stop sau timeout hoặc khi đạt terminal state (READY/FAILED)

---

