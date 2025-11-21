# Luồng Tải Sản Phẩm (Track Upload) - Chi Tiết

> **Document Version:** 1.0  
> **Last Updated:** November 16, 2024  
> **Purpose:** Mô tả chi tiết luồng đi và logic xử lý của chức năng tải sản phẩm nhạc (track) trong hệ thống Producer Workbench

---

## 📋 Mục Lục

1. [Tổng Quan](#tổng-quan)
2. [Các Thành Phần Chính](#các-thành-phần-chính)
3. [Luồng Upload Tổng Thể](#luồng-upload-tổng-thể)
4. [Chi Tiết Từng Bước](#chi-tiết-từng-bước)
5. [Quản Lý Trạng Thái](#quản-lý-trạng-thái)
6. [Xử Lý Audio](#xử-lý-audio)
7. [Phân Quyền và Bảo Mật](#phân-quyền-và-bảo-mật)
8. [Error Handling](#error-handling)

---

## 🎯 Tổng Quan

### Mục Đích
Chức năng tải sản phẩm (track) cho phép **Owner** và **Collaborator** trong một project upload các file nhạc chất lượng cao (master file) lên hệ thống để:
- Chia sẻ và review trong phòng nội bộ (milestone)
- Tự động xử lý audio với voice tag bảo vệ bản quyền
- Stream HLS chất lượng cao qua CloudFront CDN
- Quản lý version và trạng thái của các bản nhạc

### Đặc Điểm Chính
- **Upload an toàn:** Sử dụng presigned URL để upload trực tiếp lên S3
- **Xử lý bất đồng bộ:** Audio processing chạy background không block request
- **Voice tag tự động:** Sử dụng Google Cloud TTS (tiếng Việt) để tạo watermark
- **HLS streaming:** Chuyển đổi tự động sang format phù hợp với web/mobile player
- **CloudFront CDN:** Streaming hiệu suất cao, giảm latency

---

## 🧩 Các Thành Phần Chính

### 1. Controllers
- **TrackController:** Xử lý HTTP requests cho track operations
- **FileController:** Quản lý các operations upload/download files khác

### 2. Services
- **TrackService:** Business logic cho track management
- **AudioProcessingService:** Orchestrate toàn bộ audio processing pipeline
- **FileStorageService:** Interface với AWS S3 và CloudFront
- **VoiceTagTtsService (GoogleCloudTtsServiceImpl):** Text-to-Speech tiếng Việt
- **FFmpegService:** Audio processing operations (mix, convert, probe)
- **FileKeyGenerator:** Sinh S3 keys theo convention

### 3. External Services
- **AWS S3:** Object storage cho audio files
- **AWS CloudFront:** CDN cho HLS streaming
- **Google Cloud TTS:** Voice synthesis tiếng Việt
- **FFmpeg:** Audio processing tool

### 4. Database Entities
- **Track:** Entity chính lưu metadata của sản phẩm nhạc
- **Milestone:** Container chứa các tracks
- **Project/Contract:** Context nghiệp vụ
- **User:** Người tạo track

---

## 🔄 Luồng Upload Tổng Thể

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (Frontend)                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Step 1: POST /tracks
                              │ (metadata + voice tag config)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      BACKEND - Create Track                      │
│  • Validate permissions (Owner/Collaborator)                     │
│  • Validate milestone exists                                     │
│  • Validate voice tag config                                     │
│  • Create Track entity (status: UPLOADING)                       │
│  • Generate S3 key for master file                               │
│  • Generate presigned PUT URL (15 minutes)                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Response: trackId + uploadUrl
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CLIENT - Upload Master File                   │
│  • PUT file directly to S3 using presigned URL                   │
│  • Show progress bar to user                                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Step 2: POST /tracks/{id}/finalize
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   BACKEND - Finalize Upload                      │
│  • Verify user owns the track                                    │
│  • Update status to PROCESSING                                   │
│  • Trigger async audio processing                                │
│  • Return immediately (202 Accepted)                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ @Async Processing
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│               AUDIO PROCESSING PIPELINE (Async)                  │
│                                                                   │
│  ┌──────────────────────────────────────────────────┐            │
│  │ 1. Check Voice Tag Enabled?                      │            │
│  └──────────────────────────────────────────────────┘            │
│         │                                                         │
│         ├─ YES ──────────────────────────────────────┐           │
│         │                                             │           │
│         │  ┌─────────────────────────────────────┐   │           │
│         │  │ 1a. Generate Voice Tag Audio        │   │           │
│         │  │  • Call Google Cloud TTS (vi-VN)    │   │           │
│         │  │  • Save to temp file                │   │           │
│         │  │  • Upload voice tag MP3 to S3       │   │           │
│         │  └─────────────────────────────────────┘   │           │
│         │                 │                           │           │
│         │                 ▼                           │           │
│         │  ┌─────────────────────────────────────┐   │           │
│         │  │ 1b. Mix Voice Tag into Master       │   │           │
│         │  │  • Download master from S3          │   │           │
│         │  │  • Download voice tag from S3       │   │           │
│         │  │  • FFmpeg mix (~25s intervals)      │   │           │
│         │  │  • Upload mixed audio to S3         │   │           │
│         │  └─────────────────────────────────────┘   │           │
│         │                 │                           │           │
│         │                 └───────────────────────────┘           │
│         │                             │                           │
│         ├─ NO ─────────────────────┐  │                           │
│                                    │  │                           │
│                                    ▼  ▼                           │
│  ┌──────────────────────────────────────────────────┐            │
│  │ 2. Convert to HLS                                │            │
│  │  • Download audio (master or mixed) from S3      │            │
│  │  • FFmpeg convert to HLS (10s segments)          │            │
│  │  • Generate index.m3u8 + segment_*.ts files      │            │
│  │  • Upload all HLS files to S3                    │            │
│  └──────────────────────────────────────────────────┘            │
│                             │                                     │
│                             ▼                                     │
│  ┌──────────────────────────────────────────────────┐            │
│  │ 3. Extract Audio Duration                        │            │
│  │  • Use ffprobe to get duration (seconds)         │            │
│  └──────────────────────────────────────────────────┘            │
│                             │                                     │
│                             ▼                                     │
│  ┌──────────────────────────────────────────────────┐            │
│  │ 4. Update Track Status                           │            │
│  │  • Set processingStatus = READY                  │            │
│  │  • Save hlsPrefix, duration to DB                │            │
│  │  • Clear errorMessage                            │            │
│  └──────────────────────────────────────────────────┘            │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Track now READY
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  CLIENT - Poll or WebSocket                      │
│  • Check track status (processingStatus)                         │
│  • When READY: receive hlsPlaybackUrl                            │
│  • Display player with CloudFront streaming URL                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📝 Chi Tiết Từng Bước

### Bước 1: Tạo Track và Nhận Upload URL

**Endpoint:** `POST /api/v1/projects/{projectId}/milestones/{milestoneId}/tracks`

**Request Body:**
```json
{
  "name": "Track Name",
  "description": "Track description",
  "version": "v1",
  "contentType": "audio/wav",
  "fileSize": 52428800,
  "voiceTagEnabled": true,
  "voiceTagText": "Demo thuộc về Producer X, chỉ để nghe trước"
}
```

**Các Kiểm Tra (Validations):**
1. **Authentication:** User phải đăng nhập
2. **Milestone Existence:** Milestone phải tồn tại và thuộc project được chỉ định
3. **Permission Check:**
   - Nếu là **Owner** của project: Luôn được phép
   - Nếu là **Collaborator**: Phải đã **APPROVED Money Split** trước
   - Các role khác: Từ chối (403 Access Denied)
4. **Voice Tag Validation:** Nếu `voiceTagEnabled = true`, `voiceTagText` không được rỗng

**Xử Lý:**
1. Tạo Track entity với:
   - `status = INTERNAL_DRAFT` (trạng thái nghiệp vụ)
   - `processingStatus = UPLOADING` (trạng thái kỹ thuật)
   - Metadata từ request
2. Generate S3 key cho master file:
   - Pattern: `audio/original/{trackId}/master.{extension}`
3. Generate presigned PUT URL (có hiệu lực 15 phút)
4. Lưu track vào database

**Response:**
```json
{
  "code": 201,
  "message": "Đã tạo track thành công. Vui lòng upload file master.",
  "result": {
    "trackId": 123,
    "uploadUrl": "https://s3.amazonaws.com/...",
    "s3Key": "audio/original/123/master.wav",
    "expiresIn": 900
  }
}
```

---

### Bước 2: Upload File Lên S3

**Client Side:**
- Sử dụng presigned URL để PUT file trực tiếp lên S3
- Không qua backend server → giảm tải cho backend
- Có thể hiển thị progress bar cho user
- Phải hoàn thành trong 15 phút (thời gian expire của presigned URL)

**S3 Storage:**
- File được lưu tại `audio/original/{trackId}/master.{ext}`
- Bucket: Private (không public access)
- Không có processing tự động (S3 events) - tránh race condition

---

### Bước 3: Finalize Upload

**Endpoint:** `POST /api/v1/tracks/{trackId}/finalize`

**Mục đích:** Báo cho backend biết upload đã hoàn tất, trigger processing pipeline

**Các Kiểm Tra:**
1. User phải là người tạo track (track owner)
2. Track phải đang ở trạng thái `UPLOADING`

**Xử Lý:**
1. Cập nhật `processingStatus = PROCESSING` **ngay lập tức** (tránh double finalize)
2. Clear `errorMessage` nếu có
3. Trigger async audio processing bằng `@Async`
4. Return ngay với status 202 Accepted

**Response:**
```json
{
  "code": 202,
  "message": "Đã bắt đầu xử lý audio cho track. Vui lòng đợi."
}
```

---

### Bước 4: Audio Processing Pipeline (Async)

#### 4.1. Decision: Voice Tag Enabled?

**Nếu Voice Tag ENABLED:**

##### 4.1.1. Generate Voice Tag Audio
- **Input:** `voiceTagText` (ví dụ: "Demo thuộc về Producer X")
- **Service:** Google Cloud Text-to-Speech
- **Config:**
  - Language: `vi-VN` (tiếng Việt)
  - Voice: `vi-VN-Wavenet-A` (giọng nữ tự nhiên)
  - Volume: `+6.0 dB` (boost để nghe rõ khi mix)
  - Format: MP3
- **Process:**
  1. Gọi Google Cloud TTS API
  2. Nhận audio stream (MP3 format)
  3. Lưu vào temp file
  4. Upload lên S3: `audio/voice-tag/{trackId}/tag.mp3`
  5. Cleanup temp file
- **Output:** S3 key của voice tag audio

##### 4.1.2. Mix Voice Tag Into Master
- **Input:**
  - Master file S3 key
  - Voice tag file S3 key
  - Interval: 25 seconds (configurable)
- **Tool:** FFmpeg
- **Process:**
  1. Download cả master và voice tag từ S3
  2. FFmpeg command mix voice tag vào master:
     - Voice tag lặp lại mỗi 25 giây
     - Volume đã được boost (+6dB) nên nghe rõ
     - Output format: M4A (AAC codec)
  3. Upload mixed audio lên S3: `audio/mixed/{trackId}/mixed.m4a`
  4. Cleanup temp files
- **Output:** S3 key của mixed audio

**Nếu Voice Tag DISABLED:**
- Bỏ qua bước 4.1, sử dụng trực tiếp master file cho HLS conversion

---

#### 4.2. Convert to HLS

- **Input:** Audio S3 key (master hoặc mixed)
- **Tool:** FFmpeg
- **Process:**
  1. Download audio từ S3
  2. FFmpeg convert to HLS:
     - Codec: AAC 192kbps
     - Segment duration: 10 seconds
     - Output: `index.m3u8` + multiple `segment_XXX.ts` files
  3. Upload tất cả HLS files lên S3:
     - Prefix: `audio/hls/{trackId}/`
     - Files: `index.m3u8`, `segment_000.ts`, `segment_001.ts`, ...
     - Content-Type:
       - `.m3u8`: `application/vnd.apple.mpegurl`
       - `.ts`: `video/mp2t`
  4. Cleanup temp files and directory
- **Output:** HLS prefix (ví dụ: `audio/hls/123/`)

---

#### 4.3. Extract Audio Duration

- **Input:** Master audio S3 key (bản gốc, không phải mixed)
- **Tool:** ffprobe (part of FFmpeg)
- **Process:**
  1. Download master từ S3
  2. Run ffprobe để lấy duration
  3. Parse output và extract seconds
  4. Cleanup temp file
- **Output:** Duration in seconds (Integer)

---

#### 4.4. Update Track Status

**Success Case:**
- `processingStatus = READY`
- `hlsPrefix = "audio/hls/{trackId}/"`
- `duration = X` (seconds)
- `voiceTagAudioKey = "audio/voice-tag/{trackId}/tag.mp3"` (nếu có)
- `errorMessage = null`

**Failure Case:**
- `processingStatus = FAILED`
- `errorMessage = "Lỗi xử lý audio: {error detail}"`
- Các field khác giữ nguyên

---

### Bước 5: Playback

**Endpoint:** `GET /api/v1/tracks/{trackId}/playback-url`

**Mục đích:** Lấy CloudFront streaming URL để phát track

**Các Kiểm Tra:**
1. User có quyền xem track (Owner hoặc Collaborator đã approve Money Split)
2. Track ở trạng thái `READY`
3. Track có `hlsPrefix`

**Xử Lý:**
1. Construct HLS playlist key: `{hlsPrefix}index.m3u8`
2. Generate CloudFront URL:
   - Format: `https://{cloudfrontDomain}/{hlsPrefix}index.m3u8`
   - Public URL (không có signature/expiration)
   - CloudFront cache các segments để streaming hiệu suất cao

**Response:**
```json
{
  "code": 200,
  "result": "https://d123456.cloudfront.net/audio/hls/123/index.m3u8"
}
```

---

## 🔄 Quản Lý Trạng Thái

### Track Status (Trạng Thái Nghiệp Vụ)

| Status | Mô Tả | Ai Có Thể Thay Đổi |
|--------|-------|---------------------|
| **INTERNAL_DRAFT** | Track mới upload, chưa được review | Default khi tạo |
| **INTERNAL_REJECTED** | Bị nội bộ reject (chưa đạt yêu cầu) | Owner |
| **INTERNAL_APPROVED** | Đã duyệt nội bộ, sẵn sàng làm bản tham chiếu | Owner |

**Note:** Status này độc lập với Processing Status. Track có thể `READY` về mặt kỹ thuật nhưng vẫn là `INTERNAL_DRAFT` về mặt nghiệp vụ.

---

### Processing Status (Trạng Thái Kỹ Thuật)

```
UPLOADING ──finalize──> PROCESSING ──success──> READY
                            │
                            │
                           fail
                            │
                            ▼
                         FAILED
```

| Status | Mô Tả | User Actions Available |
|--------|-------|------------------------|
| **UPLOADING** | Đang chờ user upload file master | Upload file, Cancel |
| **PROCESSING** | Hệ thống đang xử lý (TTS + Mix + HLS) | None (đợi) |
| **READY** | Đã có bản HLS, có thể stream | Play, Update metadata, Re-process |
| **FAILED** | Xử lý lỗi | View error, Re-process |

**State Transitions:**
1. `UPLOADING` → `PROCESSING`: Khi gọi `/finalize`
2. `PROCESSING` → `READY`: Khi audio processing thành công
3. `PROCESSING` → `FAILED`: Khi có lỗi trong quá trình xử lý
4. `FAILED` → `PROCESSING`: Khi user trigger re-process
5. `READY` → `PROCESSING`: Khi user trigger re-process (ví dụ: thay đổi voice tag)

---

## 🎵 Xử Lý Audio

### Voice Tag Strategy

**Tại sao cần Voice Tag?**
- Bảo vệ bản quyền: Watermark âm thanh khó loại bỏ
- Xác thực nguồn gốc: Người nghe biết track thuộc ai
- Ngăn chặn sử dụng trái phép: Demo không thể dùng commercial

**Voice Tag Flow:**
```
Text Input (vi-VN)
    │
    ▼
Google Cloud TTS (vi-VN-Wavenet-A, +6dB)
    │
    ▼
Voice Tag MP3 (short audio ~3-5s)
    │
    ▼
FFmpeg Mix into Master (every 25s)
    │
    ▼
Tagged Audio (M4A)
    │
    ▼
HLS Conversion
```

**Kỹ Thuật Mix:**
- **Interval:** 25 seconds (có thể config)
- **Volume:** Voice tag +6dB so với default để nghe rõ
- **Method:** Overlay (không thay thế audio gốc)
- **Format Output:** M4A (AAC codec, universal compatibility)

---

### HLS Conversion

**Tại sao HLS?**
- **Adaptive streaming:** Tự động điều chỉnh chất lượng theo băng thông
- **Universal support:** iOS, Android, Web browsers
- **Efficient:** Streaming segments, không cần download toàn bộ file
- **CDN friendly:** Segments cache tốt trên CloudFront

**Conversion Specs:**
- **Codec:** AAC (audio/mp4)
- **Bitrate:** 192 kbps (chất lượng cao, balanced với file size)
- **Segment Duration:** 10 seconds
- **Playlist:** `index.m3u8` (HLS master playlist)
- **Segments:** `segment_000.ts`, `segment_001.ts`, ...

**S3 Structure:**
```
audio/
├── original/
│   └── {trackId}/
│       └── master.wav         (original file)
├── voice-tag/
│   └── {trackId}/
│       └── tag.mp3            (TTS voice tag)
├── mixed/
│   └── {trackId}/
│       └── mixed.m4a          (master + voice tag)
└── hls/
    └── {trackId}/
        ├── index.m3u8         (playlist)
        ├── segment_000.ts     (10s segment)
        ├── segment_001.ts
        └── ...
```

---

### CloudFront Streaming

**Setup:**
- **Origin:** S3 bucket (private)
- **Behavior:** `/audio/hls/*` path pattern
- **Caching:** Standard cache policy cho `.m3u8` và `.ts`
- **Security:** No signed URLs (nội bộ project, đã check permission ở backend)

**URL Generation:**
- Backend generate: `https://{cloudfrontDomain}/audio/hls/{trackId}/index.m3u8`
- Client player (HLS.js, AVPlayer, ExoPlayer) load playlist
- CloudFront serve segments với low latency
- S3 Origin chỉ fetch khi cache miss

**Benefits:**
- **Low latency:** Edge locations gần user
- **High throughput:** Không giới hạn concurrent connections
- **Cost effective:** Cache giảm S3 GET requests
- **Reliable:** Auto failover, 99.9% uptime SLA

---

## 🔒 Phân Quyền và Bảo Mật

### Upload Permission

| Role | Điều Kiện | Quyền Upload |
|------|-----------|--------------|
| **Owner** | Tạo project | ✅ Luôn được phép |
| **Collaborator** | Đã approve Money Split | ✅ Được phép |
| **Collaborator** | Chưa approve Money Split | ❌ Không được phép |
| **Viewer** | - | ❌ Không được phép |
| **Non-member** | - | ❌ Không được phép |

**Logic:**
```
if (user is Owner) {
    ALLOW upload
} else if (user is Collaborator AND has approved Money Split) {
    ALLOW upload
} else {
    DENY with "Access Denied" error
}
```

---

### View/Play Permission

**Tương tự Upload Permission:**
- Owner: ✅ Luôn xem được
- Collaborator (approved Money Split): ✅ Xem được
- Others: ❌ Không xem được

**Lý do cần Money Split approval:**
- Collaborator phải commit vào project (chấp nhận chia sẻ doanh thu)
- Chống abuse: Không cho user "xem trộm" mà không tham gia project

---

### Update Permission

| Role | Điều Kiện Thêm | Quyền Update |
|------|----------------|--------------|
| **Owner** | - | ✅ Update bất kỳ track nào |
| **Collaborator** | Track do mình tạo | ✅ Update track của mình |
| **Collaborator** | Track của người khác | ❌ Không được update |

**Business Rule:**
- Collaborator chỉ được sửa track của chính mình
- Owner có thể sửa tất cả tracks trong project

---

### Delete Permission

**Chỉ Owner có thể xóa track**

**Lý do:**
- Track là tài sản của project
- Owner chịu trách nhiệm quản lý toàn bộ project
- Tránh Collaborator xóa track của người khác

---

## ⚠️ Error Handling

### Upload Phase Errors

| Error | Cause | User Action |
|-------|-------|-------------|
| **403 Access Denied** | Không có quyền upload (chưa approve Money Split) | Approve Money Split trước |
| **404 Milestone Not Found** | Milestone không tồn tại hoặc không thuộc project | Kiểm tra lại milestone ID |
| **400 Voice Tag Invalid** | Bật voice tag nhưng text rỗng | Nhập voice tag text hoặc tắt voice tag |
| **400 Presigned URL Expired** | Upload quá 15 phút | Tạo track mới và upload lại |

---

### Processing Phase Errors

| Error | Cause | Recovery |
|-------|-------|----------|
| **TTS Failed** | Google Cloud TTS API error, credentials sai | Admin fix credentials, user re-process |
| **FFmpeg Mix Failed** | FFmpeg crash, file corrupt, không đủ RAM | Check file integrity, admin check server resources |
| **HLS Conversion Failed** | FFmpeg crash, unsupported audio format | User upload lại với format khác (WAV/MP3/FLAC) |
| **S3 Upload Failed** | Network error, S3 permissions | Retry, admin check S3 permissions |
| **File Not Found** | Master file bị xóa trước khi process | Upload lại |

**Error Information:**
- `processingStatus = FAILED`
- `errorMessage`: Chi tiết lỗi (hiển thị cho user)
- Log: Full stack trace (cho admin debug)

**Recovery Options:**
1. **Re-process:** Trigger lại processing pipeline (keep master file)
2. **Delete & Re-upload:** Xóa track và upload lại từ đầu
3. **Contact Support:** Nếu lỗi liên quan infrastructure

---

### Playback Phase Errors

| Error | Cause | User Action |
|-------|-------|-------------|
| **403 Access Denied** | Không có quyền xem | Approve Money Split |
| **400 Not Ready** | Track chưa READY (đang PROCESSING/FAILED) | Đợi hoặc check error |
| **404 HLS Not Found** | HLS files bị xóa | Admin restore hoặc re-process |
| **CloudFront 403** | CloudFront config sai | Admin fix CloudFront origin access |
| **Player Error** | Unsupported browser, network issue | Update browser, check network |

---

## 📊 Workflow Diagram - State Machine

```
┌─────────────────────────────────────────────────────────────┐
│                      TRACK LIFECYCLE                         │
└─────────────────────────────────────────────────────────────┘

                        [CREATE TRACK]
                              │
                              ▼
                    ╔═══════════════════╗
                    ║     UPLOADING     ║ ◄──── User upload file to S3
                    ╚═══════════════════╝
                              │
                              │ /finalize
                              ▼
                    ╔═══════════════════╗
                    ║    PROCESSING     ║ ◄──── Async: TTS + Mix + HLS
                    ╚═══════════════════╝
                        │          │
                        │          │
                   success      error
                        │          │
            ┌───────────┘          └───────────┐
            ▼                                   ▼
   ╔═══════════════════╗              ╔═══════════════════╗
   ║       READY       ║              ║      FAILED       ║
   ╚═══════════════════╝              ╚═══════════════════╝
            │                                   │
            │ re-process                        │ re-process
            └───────────┐          ┌────────────┘
                        │          │
                        ▼          ▼
                    ╔═══════════════════╗
                    ║    PROCESSING     ║
                    ╚═══════════════════╝


Business Status (Independent):
───────────────────────────────
INTERNAL_DRAFT ──review──> INTERNAL_APPROVED
       │
       └──reject──> INTERNAL_REJECTED
```

---

## 🎯 Performance Considerations

### Upload Performance
- **Direct S3 Upload:** Client → S3 (không qua backend)
- **Presigned URL:** Không expose credentials
- **Large File Support:** Lên đến 1GB (config: `spring.servlet.multipart.max-file-size`)

### Processing Performance
- **Async Execution:** Không block HTTP request
- **Thread Pool:** `@Async` sử dụng Spring thread pool
- **Parallel Processing:** Nhiều tracks có thể process đồng thời
- **Temp File Cleanup:** Tự động cleanup để không leak disk space

### Streaming Performance
- **CloudFront CDN:** Edge caching, global distribution
- **HLS Segments:** 10s segments, progressive download
- **Bitrate:** 192kbps AAC (balanced quality/size)
- **Cache Hit Ratio:** Cao cho popular tracks

---

## 📈 Scalability

### Bottlenecks
1. **FFmpeg Processing:** CPU intensive, cần scale server resources
2. **S3 Bandwidth:** Upload/download lớn, cần monitor costs
3. **Google Cloud TTS:** Quota limits, cần monitor usage

### Solutions
1. **Horizontal Scaling:** Deploy multiple backend instances
2. **Dedicated Processing Workers:** Tách audio processing ra service riêng
3. **Queue System:** Dùng message queue (Kafka/RabbitMQ) cho processing jobs
4. **Caching:** Cache TTS results cho voice tag text giống nhau
5. **CDN:** CloudFront handle streaming load

---

## 🔍 Monitoring & Logging

### Key Metrics
- **Upload Success Rate:** % tracks hoàn thành upload
- **Processing Success Rate:** % tracks xử lý thành công
- **Processing Duration:** Thời gian trung bình từ finalize → READY
- **Error Rate by Type:** TTS errors, FFmpeg errors, S3 errors
- **Storage Usage:** Total size của tracks trên S3
- **CloudFront Cache Hit Ratio:** % requests served từ cache

### Logging Points
- Track created (trackId, user, milestone)
- Upload finalized (trackId, fileSize)
- Processing started/completed/failed (trackId, duration, error)
- TTS called (trackId, text length, voice config)
- FFmpeg operations (trackId, operation type, input/output size)
- HLS uploaded (trackId, segment count)
- Playback URL generated (trackId, user)

---

## 🛠️ Configuration

### Application Properties
```yaml
# S3 Storage
aws.s3.bucket-name: ${AWS_S3_BUCKET_NAME}

# CloudFront
cloudfront.domain: ${CLOUDFRONT_DOMAIN}

# Google Cloud TTS
gcp.tts.language-code: vi-VN
gcp.tts.voice-name: vi-VN-Wavenet-A
gcp.tts.volume-gain-db: 6.0

# FFmpeg
ffmpeg.path: /usr/bin/ffmpeg
ffprobe.path: /usr/bin/ffprobe
ffmpeg.voice-tag.interval-seconds: 25

# File Upload
spring.servlet.multipart.max-file-size: 1GB
spring.servlet.multipart.max-request-size: 1GB

# Temp Storage
storage.base-dir: /var/pwb-files
```

### Environment Variables
- `AWS_ACCESS_KEY_ID`: AWS credentials
- `AWS_SECRET_ACCESS_KEY`: AWS credentials
- `AWS_REGION`: S3 region
- `GOOGLE_APPLICATION_CREDENTIALS`: Path to GCP service account JSON
- `CLOUDFRONT_DOMAIN`: CloudFront distribution domain

---

## ✅ Success Criteria

Track upload được coi là **thành công** khi:
1. ✅ Track entity được tạo trong database
2. ✅ Master file được upload lên S3 (original key)
3. ✅ Finalize endpoint được gọi thành công
4. ✅ Audio processing hoàn tất không lỗi:
   - Voice tag (nếu enabled) được tạo và mix thành công
   - HLS conversion thành công, tạo đủ segments
   - Duration được extract chính xác
5. ✅ Track status = READY
6. ✅ HLS playback URL có thể generate được
7. ✅ Client player có thể stream track qua CloudFront

---

## 📚 Related Documents

- **MIGRATION_SUMMARY.md**: Tổng quan migration AWS Polly → Google Cloud TTS
- **API Documentation**: Full REST API specs cho track endpoints
- **Database Schema**: Track entity và relationships
- **CloudFront Setup**: CDN configuration guide

---

## 🔄 Future Enhancements

### Planned
- [ ] Batch upload multiple tracks
- [ ] Background music cho voice tag
- [ ] Multiple voice tag voices (male/female options)
- [ ] Advanced HLS: Multiple quality profiles (adaptive bitrate)
- [ ] Waveform visualization generation
- [ ] Automatic audio normalization
- [ ] AI-powered audio quality check

### Under Consideration
- [ ] Real-time collaborative listening sessions
- [ ] Audio version comparison tool
- [ ] Automatic mixing/mastering suggestions
- [ ] Integration with DAW plugins
- [ ] Blockchain-based copyright proof

---

**Document End** 📄

