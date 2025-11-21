# API Documentation - Track Comments cho Frontend

> **Document Version:** 1.0  
> **Last Updated:** December 2024  
> **Audience:** Frontend Developers  
> **Purpose:** Hướng dẫn gọi API để implement chức năng comment trên tracks

---

## 📋 Mục Lục

1. [Tổng Quan](#tổng-quan)
2. [Authentication](#authentication)
3. [API Endpoints](#api-endpoints)
4. [Data Models](#data-models)
5. [Error Handling](#error-handling)
6. [Use Cases](#use-cases)

---

## 🎯 Tổng Quan

Hệ thống comment trên track cho phép:
- Tạo comment gắn với timestamp cụ thể trong track
- Tạo comment chung (không gắn timestamp)
- **Reply cho comment nhiều cấp (nested replies) - giống Facebook**
- Reply của reply (threaded conversations)
- Cập nhật trạng thái comment (PENDING, IN_PROGRESS, RESOLVED)
- Xem thống kê comment theo status
- Lấy comments tại một timestamp cụ thể

### Comment Structure (Nested Replies)

Hệ thống hỗ trợ **nested replies không giới hạn cấp độ**, giống như Facebook:

```
📝 Comment gốc (parentCommentId: null)
  ├─ 💬 Reply 1 (parentCommentId: comment_id)
  │   ├─ 💬 Reply của Reply 1 (parentCommentId: reply_1_id)
  │   │   └─ 💬 Reply của Reply của Reply 1 (nested sâu hơn)
  │   └─ 💬 Reply khác của Reply 1
  └─ 💬 Reply 2 (parentCommentId: comment_id)
```

**Cách hoạt động:**
- Comment gốc: `parentCommentId = null`
- Reply: `parentCommentId = ID của comment muốn reply`
- Reply của reply: `parentCommentId = ID của reply muốn reply`
- Không giới hạn độ sâu của nested replies

### Comment Status Flow

```
PENDING → IN_PROGRESS → RESOLVED
  ↑           ↑            ↑
  └───────────┴────────────┘
   (Track owner có thể chuyển đổi giữa các status)
```

---

## 🔐 Authentication

### Required Header

Tất cả API calls cần header:

```
Authorization: Bearer {access_token}
Content-Type: application/json
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

## 📡 1. Create Comment

### Endpoint

```
POST /tracks/{trackId}/comments
```

### Purpose

Tạo comment mới trên track hoặc reply cho comment (hỗ trợ nested replies nhiều cấp). Tự động gửi email thông báo cho track owner.

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `trackId` | Long | ✅ | ID của track |

### Request Body

```json
{
  "content": "Đoạn này bass hơi nặng, có thể giảm một chút không?",
  "timestamp": 45,
  "parentCommentId": null
}
```

**Fields:**
- `content` (String, required): Nội dung comment (tối đa 2000 ký tự)
- `timestamp` (Integer, optional): Timestamp trong track (giây). Null nếu comment chung hoặc reply
- `parentCommentId` (Long, optional): ID comment cha nếu đây là reply. Null nếu là comment gốc
  - Để tạo **reply của reply (nested)**, set `parentCommentId` = ID của reply muốn trả lời
  - Hỗ trợ nhiều cấp: Reply → Reply → Reply... (không giới hạn)

### Response (201 Created)

```json
{
  "code": 201,
  "message": "Đã tạo comment thành công",
  "result": {
    "id": 1,
    "trackId": 1,
    "user": {
      "id": 1,
      "firstName": "John",
      "lastName": "Doe",
      "fullName": "John Doe",
      "email": "john@example.com",
      "avatarUrl": "https://..."
    },
    "content": "Đoạn này bass hơi nặng, có thể giảm một chút không?",
    "timestamp": 45,
    "status": "PENDING",
    "parentCommentId": null,
    "replyCount": 0,
    "replies": null,
    "createdAt": "2024-12-01T10:00:00Z",
    "updatedAt": "2024-12-01T10:00:00Z"
  }
}
```

### Error Responses

**400 Bad Request** - Validation error
```json
{
  "code": 400,
  "message": "Nội dung comment không được để trống"
}
```

**404 Not Found** - Track không tồn tại
```json
{
  "code": 404,
  "message": "Track không tồn tại"
}
```

---

## 📡 2. Get Root Comments (Pagination)

### Endpoint

```
GET /tracks/{trackId}/comments
```

### Purpose

Lấy danh sách comment gốc (không có parent) của track với pagination. Sắp xếp theo timestamp tăng dần.

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `trackId` | Long | ✅ | ID của track |

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | Integer | ❌ | 0 | Số trang (bắt đầu từ 0) |
| `size` | Integer | ❌ | 20 | Số lượng items mỗi trang (tối đa 100) |

### Response (200 OK)

```json
{
  "code": 200,
  "message": "Lấy danh sách comment thành công",
  "result": {
    "content": [
      {
        "id": 1,
        "trackId": 1,
        "user": { ... },
        "content": "Comment 1",
        "timestamp": 45,
        "status": "PENDING",
        "parentCommentId": null,
        "replyCount": 2,
        "replies": null,
        "createdAt": "2024-12-01T10:00:00Z",
        "updatedAt": "2024-12-01T10:00:00Z"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20
    },
    "totalElements": 10,
    "totalPages": 1,
    "last": true,
    "first": true,
    "numberOfElements": 10
  }
}
```

---

## 📡 3. Get Comment By ID

### Endpoint

```
GET /comments/{commentId}
```

### Purpose

Lấy thông tin chi tiết một comment (bao gồm replies nếu có).

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `commentId` | Long | ✅ | ID của comment |

### Response (200 OK)

```json
{
  "code": 200,
  "message": "Lấy thông tin comment thành công",
  "result": {
    "id": 1,
    "trackId": 1,
    "user": { ... },
    "content": "Comment content",
    "timestamp": 45,
    "status": "PENDING",
    "parentCommentId": null,
    "replyCount": 2,
    "replies": [
      {
        "id": 2,
        "trackId": 1,
        "user": { ... },
        "content": "Reply 1",
        "timestamp": null,
        "status": "PENDING",
        "parentCommentId": 1,
        "replyCount": 0,
        "replies": null,
        "createdAt": "2024-12-01T10:05:00Z",
        "updatedAt": "2024-12-01T10:05:00Z"
      }
    ],
    "createdAt": "2024-12-01T10:00:00Z",
    "updatedAt": "2024-12-01T10:00:00Z"
  }
}
```

### Error Responses

**404 Not Found** - Comment không tồn tại
```json
{
  "code": 404,
  "message": "Comment không tồn tại"
}
```

---

## 📡 4. Get Replies By Comment

### Endpoint

```
GET /comments/{commentId}/replies
```

### Purpose

Lấy danh sách tất cả replies của một comment.

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `commentId` | Long | ✅ | ID của comment cha |

### Response (200 OK)

```json
{
  "code": 200,
  "message": "Lấy danh sách reply thành công",
  "result": [
    {
      "id": 2,
      "trackId": 1,
      "user": { ... },
      "content": "Reply 1",
      "timestamp": null,
      "status": "PENDING",
      "parentCommentId": 1,
      "replyCount": 0,
      "replies": null,
      "createdAt": "2024-12-01T10:05:00Z",
      "updatedAt": "2024-12-01T10:05:00Z"
    }
  ]
}
```

---

## 📡 5. Update Comment

### Endpoint

```
PUT /comments/{commentId}
```

### Purpose

Cập nhật nội dung comment. Chỉ user tạo comment mới có quyền.

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `commentId` | Long | ✅ | ID của comment |

### Request Body

```json
{
  "content": "Đoạn này bass hơi nặng, có thể giảm một chút không? (Đã chỉnh sửa)"
}
```

**Fields:**
- `content` (String, required): Nội dung comment mới (tối đa 2000 ký tự)

### Response (200 OK)

```json
{
  "code": 200,
  "message": "Đã cập nhật comment thành công",
  "result": {
    "id": 1,
    "content": "Đoạn này bass hơi nặng, có thể giảm một chút không? (Đã chỉnh sửa)",
    ...
  }
}
```

### Error Responses

**403 Forbidden** - Không có quyền
```json
{
  "code": 403,
  "message": "Chỉ user tạo comment mới được phép cập nhật"
}
```

**404 Not Found** - Comment không tồn tại
```json
{
  "code": 404,
  "message": "Comment không tồn tại"
}
```

---

## 📡 6. Delete Comment

### Endpoint

```
DELETE /comments/{commentId}
```

### Purpose

Xóa comment (soft delete). Chỉ user tạo comment hoặc track owner mới có quyền.

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `commentId` | Long | ✅ | ID của comment |

### Response (200 OK)

```json
{
  "code": 200,
  "message": "Đã xóa comment thành công"
}
```

### Error Responses

**403 Forbidden** - Không có quyền
```json
{
  "code": 403,
  "message": "Chỉ user tạo comment hoặc track owner mới được phép xóa"
}
```

---

## 📡 7. Update Comment Status

### Endpoint

```
PUT /comments/{commentId}/status
```

### Purpose

Cập nhật trạng thái của comment. Chỉ track owner mới có quyền. Tự động gửi email thông báo cho comment owner.

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `commentId` | Long | ✅ | ID của comment |

### Request Body

```json
{
  "status": "IN_PROGRESS"
}
```

**Fields:**
- `status` (CommentStatus, required): Trạng thái mới
  - `PENDING`: Chưa xử lý
  - `IN_PROGRESS`: Đang xử lý
  - `RESOLVED`: Đã xử lý xong

### Response (200 OK)

```json
{
  "code": 200,
  "message": "Đã cập nhật trạng thái comment thành công",
  "result": {
    "id": 1,
    "status": "IN_PROGRESS",
    ...
  }
}
```

### Error Responses

**403 Forbidden** - Không có quyền
```json
{
  "code": 403,
  "message": "Chỉ track owner mới được phép cập nhật trạng thái comment"
}
```

**400 Bad Request** - Status không hợp lệ
```json
{
  "code": 400,
  "message": "Trạng thái không được để trống"
}
```

---

## 📡 8. Get Comment Statistics

### Endpoint

```
GET /tracks/{trackId}/comments/statistics
```

### Purpose

Lấy thống kê comment của track theo status.

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `trackId` | Long | ✅ | ID của track |

### Response (200 OK)

```json
{
  "code": 200,
  "message": "Lấy thống kê comment thành công",
  "result": {
    "trackId": 1,
    "totalComments": 15,
    "pendingComments": 5,
    "inProgressComments": 3,
    "resolvedComments": 7
  }
}
```

---

## 📡 9. Get Comments By Timestamp

### Endpoint

```
GET /tracks/{trackId}/comments/by-timestamp
```

### Purpose

Lấy danh sách comment tại một timestamp cụ thể trong track. Hữu ích để hiển thị comments tại điểm thời gian cụ thể trong player.

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `trackId` | Long | ✅ | ID của track |

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `timestamp` | Integer | ✅ | Timestamp trong track (giây) |

### Response (200 OK)

```json
{
  "code": 200,
  "message": "Lấy comment theo timestamp thành công",
  "result": [
    {
      "id": 1,
      "trackId": 1,
      "user": { ... },
      "content": "Comment tại timestamp 45",
      "timestamp": 45,
      "status": "PENDING",
      "parentCommentId": null,
      "replyCount": 1,
      "replies": null,
      "createdAt": "2024-12-01T10:00:00Z",
      "updatedAt": "2024-12-01T10:00:00Z"
    }
  ]
}
```

### Error Responses

**400 Bad Request** - Timestamp không hợp lệ
```json
{
  "code": 400,
  "message": "Timestamp không hợp lệ"
}
```

---

## 📊 Data Models

### TrackCommentResponse

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | ID của comment |
| `trackId` | Long | ID của track |
| `user` | UserBasicInfo | Thông tin user tạo comment |
| `content` | String | Nội dung comment |
| `timestamp` | Integer | Timestamp trong track (giây), null nếu comment chung |
| `status` | CommentStatus | Trạng thái: PENDING, IN_PROGRESS, RESOLVED |
| `parentCommentId` | Long | ID comment cha (null nếu là comment gốc) |
| `replyCount` | Long | Số lượng reply |
| `replies` | List<TrackCommentResponse> | Danh sách reply (null nếu chưa load) |
| `createdAt` | Date | Thời gian tạo |
| `updatedAt` | Date | Thời gian cập nhật |

### UserBasicInfo

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | ID của user |
| `firstName` | String | Tên |
| `lastName` | String | Họ |
| `fullName` | String | Họ tên đầy đủ |
| `email` | String | Email |
| `avatarUrl` | String | URL avatar |

### TrackCommentStatisticsResponse

| Field | Type | Description |
|-------|------|-------------|
| `trackId` | Long | ID của track |
| `totalComments` | Long | Tổng số comment |
| `pendingComments` | Long | Số comment đang chờ xử lý |
| `inProgressComments` | Long | Số comment đang được xử lý |
| `resolvedComments` | Long | Số comment đã xử lý xong |

### CommentStatus Enum

- `PENDING`: Chưa xử lý - trạng thái mặc định khi comment mới được tạo
- `IN_PROGRESS`: Đang xử lý - track owner đang xử lý feedback này
- `RESOLVED`: Đã xử lý - track owner đã hoàn thành xử lý feedback

---

## ⚠️ Error Handling

### Common Error Codes

| Code | Description | Action |
|------|-------------|--------|
| 400 | Bad Request - Validation error | Kiểm tra lại request body |
| 401 | Unauthorized - Token không hợp lệ | Refresh token hoặc đăng nhập lại |
| 403 | Forbidden - Không có quyền | Kiểm tra quyền truy cập |
| 404 | Not Found - Resource không tồn tại | Kiểm tra ID trong path parameter |
| 500 | Internal Server Error | Thử lại sau hoặc liên hệ support |

### Error Response Format

```json
{
  "code": 400,
  "message": "Mô tả lỗi chi tiết"
}
```

---

## 💡 Use Cases

### 1. Hiển thị danh sách comment trên track

1. Gọi `GET /tracks/{trackId}/comments?page=0&size=20` để lấy comment gốc
2. Hiển thị danh sách với pagination
3. Khi user click vào một comment, gọi `GET /comments/{commentId}/replies` để load replies
4. Hoặc gọi `GET /comments/{commentId}` để lấy comment kèm replies

### 2. Tạo comment tại timestamp cụ thể

1. User click vào timeline tại vị trí muốn comment
2. Lấy timestamp từ player (giây)
3. Gọi `POST /tracks/{trackId}/comments` với `timestamp` và `content`
4. Refresh danh sách comment hoặc thêm comment mới vào UI

### 3. Tạo reply cho comment

1. User click "Reply" trên một comment
2. Gọi `POST /tracks/{trackId}/comments` với `parentCommentId` = ID comment cha
3. `timestamp` có thể null cho reply
4. Refresh replies của comment đó

### 4. Hiển thị comments tại timestamp khi play

1. Lắng nghe event `timeupdate` từ audio player
2. Lấy timestamp hiện tại (giây)
3. Gọi `GET /tracks/{trackId}/comments/by-timestamp?timestamp={currentTime}`
4. Hiển thị comments tại timestamp đó (có thể dùng tooltip hoặc sidebar)

### 5. Track owner quản lý comment status

1. Hiển thị dropdown/buttons để chọn status (PENDING, IN_PROGRESS, RESOLVED)
2. Gọi `PUT /comments/{commentId}/status` với status mới
3. Cập nhật UI với status mới
4. Có thể gọi `GET /tracks/{trackId}/comments/statistics` để hiển thị thống kê

### 6. Filter comments theo status

1. Gọi `GET /tracks/{trackId}/comments/statistics` để lấy thống kê
2. Hiển thị filter buttons (All, Pending, In Progress, Resolved)
3. Khi user chọn filter, gọi `GET /tracks/{trackId}/comments` và filter ở client-side
4. Hoặc implement filter ở backend (nếu cần)

---

## 📝 Notes

- Tất cả timestamps tính bằng giây (seconds)
- Comment có thể không có timestamp (comment chung)
- Reply không cần timestamp (thường null)
- Track owner có thể cập nhật status của bất kỳ comment nào trên track của họ
- Comment owner chỉ có thể cập nhật/xóa comment của chính họ
- Email notification tự động gửi khi:
  - Comment mới được tạo → gửi cho track owner
  - Comment status được cập nhật → gửi cho comment owner
- Pagination: mặc định page=0, size=20, tối đa size=100



