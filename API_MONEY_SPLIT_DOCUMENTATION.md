# Tài liệu API Phân chia tiền (Money Split)

## Tổng quan

Chức năng phân chia tiền cho phép OWNER của dự án phân chia số tiền của milestone cho các thành viên (COLLABORATOR, OBSERVER) và quản lý chi phí. Các thành viên có thể chấp nhận hoặc từ chối phân chia tiền được gửi cho họ.

**Base URL:** `/api/v1/projects/{projectId}/milestones/{milestoneId}`

**Lưu ý:**
- Tất cả API đều yêu cầu authentication (JWT token trong header)
- Chỉ OWNER mới có quyền tạo, cập nhật, xóa phân chia tiền và chi phí
- Chỉ người được phân chia tiền mới có quyền chấp nhận/từ chối
- Tổng số tiền phân chia + chi phí không được vượt quá số tiền của milestone

---

## API kiểm tra quyền (Permission API)

### API kiểm tra quyền Project

**Endpoint:** `GET /api/v1/projects/{projectId}/permissions`

**Mô tả:** Kiểm tra quyền của user trong project, bao gồm ProjectRole.

**Response (200 OK):**
```json
{
  "code": 200,
  "result": {
    "canCreateProject": false,
    "canInviteMembers": true,
    "canViewProject": true,
    "canEditProject": true,
    "canDeleteProject": true,
    "canViewMembers": true,
    "canManageInvitations": true,
    "canAcceptInvitation": false,
    "canDeclineInvitation": false,
    "canViewMyInvitations": true,
    "userRole": "PRODUCER",
    "projectRole": "OWNER",
    "isProjectOwner": true,
    "isProjectMember": true,
    "reason": null
  }
}
```

**Response Fields:**
- `userRole`: Vai trò của user trong hệ thống (PRODUCER, ADMIN, CUSTOMER)
- `projectRole`: Vai trò của user trong project (OWNER, CLIENT, COLLABORATOR, OBSERVER)
- `isProjectOwner`: Có phải chủ dự án không
- `isProjectMember`: Có phải thành viên của dự án không

**ProjectRole values:**
- `OWNER`: Chủ dự án - có quyền tạo, cập nhật, xóa phân chia tiền và chi phí
- `CLIENT`: Khách hàng - chỉ có quyền xem
- `COLLABORATOR`: Cộng tác viên - có thể được phân chia tiền, có quyền chấp nhận/từ chối
- `OBSERVER`: Người quan sát - có thể được phân chia tiền, có quyền chấp nhận/từ chối

**Helper methods trong ProjectPermissionResponse:**
- `isOwner()`: Kiểm tra có phải OWNER không
- `isClient()`: Kiểm tra có phải CLIENT không
- `isCollaborator()`: Kiểm tra có phải COLLABORATOR không
- `isObserver()`: Kiểm tra có phải OBSERVER không

---

## 1. Tạo phân chia tiền

**Endpoint:** `POST /api/v1/projects/{projectId}/milestones/{milestoneId}/money-splits`

**Mô tả:** OWNER tạo phân chia tiền cho một thành viên trong milestone.

**Quyền:** Chỉ OWNER

**Request Body:**
```json
{
  "userId": 123,
  "amount": "1000000.00",
  "note": "Phân chia tiền cho công việc thiết kế"
}
```

**Request Parameters:**
- `userId` (Long, required): ID của user được phân chia tiền
- `amount` (String, required): Số tiền (format: "1000000.00")
- `note` (String, optional): Ghi chú

**Validation:**
- User phải là thành viên của milestone
- User không được là OWNER hoặc CLIENT của dự án
- Tổng số tiền (phân chia + chi phí) không được vượt quá milestone amount
- Amount phải > 0
- Có thể tạo nhiều phân chia tiền cho cùng một user

**Response (201 Created):**
```json
{
  "code": 201,
  "message": "Tạo phân chia tiền thành công",
  "result": {
    "id": 1,
    "userId": 123,
    "userName": "Nguyễn Văn A",
    "userEmail": "nguyenvana@example.com",
    "amount": 1000000.00,
    "status": "PENDING",
    "note": "Phân chia tiền cho công việc thiết kế",
    "rejectionReason": null,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  }
}
```

**Error Codes:**
- `ACCESS_DENIED`: Không có quyền (không phải OWNER hoặc user là OWNER/CLIENT)
- `USER_NOT_FOUND`: User không tồn tại
- `USER_NOT_IN_PROJECT`: User không phải thành viên của milestone
- `INVALID_PARAMETER_FORMAT`: Amount <= 0 hoặc format không hợp lệ
- `MONEY_SPLIT_TOTAL_EXCEEDS_MILESTONE`: Tổng số tiền vượt quá milestone amount

---

## 2. Cập nhật phân chia tiền

**Endpoint:** `PUT /api/v1/projects/{projectId}/milestones/{milestoneId}/money-splits/{moneySplitId}`

**Mô tả:** OWNER cập nhật thông tin phân chia tiền (chỉ khi status = PENDING).

**Quyền:** Chỉ OWNER

**Request Body:**
```json
{
  "amount": "1500000.00",
  "note": "Cập nhật số tiền phân chia"
}
```

**Request Parameters:**
- `amount` (String, required): Số tiền mới
- `note` (String, optional): Ghi chú mới

**Validation:**
- Chỉ có thể cập nhật khi status = PENDING
- Không thể cập nhật khi status = APPROVED hoặc REJECTED
- Tổng số tiền không được vượt quá milestone amount
- Amount phải > 0

**Response (200 OK):**
```json
{
  "code": 200,
  "message": "Cập nhật phân chia tiền thành công",
  "result": {
    "id": 1,
    "userId": 123,
    "userName": "Nguyễn Văn A",
    "userEmail": "nguyenvana@example.com",
    "amount": 1500000.00,
    "status": "PENDING",
    "note": "Cập nhật số tiền phân chia",
    "rejectionReason": null,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T11:00:00"
  }
}
```

**Error Codes:**
- `ACCESS_DENIED`: Không có quyền hoặc money split không thuộc milestone này
- `MONEY_SPLIT_NOT_FOUND`: Không tìm thấy phân chia tiền
- `MONEY_SPLIT_CANNOT_UPDATE_APPROVED`: Không thể cập nhật khi đã APPROVED
- `MONEY_SPLIT_CANNOT_UPDATE_REJECTED`: Không thể cập nhật khi đã REJECTED
- `MONEY_SPLIT_TOTAL_EXCEEDS_MILESTONE`: Tổng số tiền vượt quá milestone amount

---

## 3. Xóa phân chia tiền

**Endpoint:** `DELETE /api/v1/projects/{projectId}/milestones/{milestoneId}/money-splits/{moneySplitId}`

**Mô tả:** OWNER xóa phân chia tiền. Chỉ không được xóa khi status = APPROVED (đã được chấp nhận). Có thể xóa khi status = PENDING hoặc REJECTED.

**Quyền:** Chỉ OWNER

**Validation:**
- Không thể xóa khi status = APPROVED (đã được chấp nhận)
- Có thể xóa khi status = PENDING (đang chờ phản hồi)
- Có thể xóa khi status = REJECTED (đã bị từ chối)

**Response (200 OK):**
```json
{
  "code": 200,
  "message": "Xóa phân chia tiền thành công"
}
```

**Error Codes:**
- `ACCESS_DENIED`: Không có quyền hoặc money split không thuộc milestone này
- `MONEY_SPLIT_NOT_FOUND`: Không tìm thấy phân chia tiền
- `MONEY_SPLIT_CANNOT_DELETE_APPROVED`: Không thể xóa phân chia tiền đã được chấp nhận. Chỉ có thể xóa phân chia tiền đang chờ phản hồi hoặc đã bị từ chối

---

## 4. Chấp nhận phân chia tiền

**Endpoint:** `POST /api/v1/projects/{projectId}/milestones/{milestoneId}/money-splits/{moneySplitId}/approve`

**Mô tả:** Thành viên được phân chia tiền chấp nhận phân chia tiền.

**Quyền:** Chỉ người được phân chia tiền (userId trong money split)

**Request Body:**
```json
{
  "rejectionReason": null
}
```

**Request Parameters:**
- `rejectionReason` (String, optional): Không cần thiết cho approve, có thể null

**Validation:**
- Chỉ có thể approve khi status = PENDING
- Không thể approve khi đã APPROVED hoặc REJECTED
- Chỉ người được phân chia tiền mới có quyền approve

**Response (200 OK):**
```json
{
  "code": 200,
  "message": "Chấp nhận phân chia tiền thành công",
  "result": {
    "id": 1,
    "userId": 123,
    "userName": "Nguyễn Văn A",
    "userEmail": "nguyenvana@example.com",
    "amount": 1000000.00,
    "status": "APPROVED",
    "note": "Phân chia tiền cho công việc thiết kế",
    "rejectionReason": null,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T12:00:00"
  }
}
```

**Error Codes:**
- `ACCESS_DENIED`: Money split không thuộc milestone này
- `MONEY_SPLIT_NOT_FOUND`: Không tìm thấy phân chia tiền
- `MONEY_SPLIT_ONLY_MEMBER_CAN_APPROVE`: Chỉ người được phân chia tiền mới có quyền approve
- `MONEY_SPLIT_ALREADY_APPROVED`: Đã được chấp nhận rồi
- `MONEY_SPLIT_ALREADY_REJECTED`: Đã bị từ chối rồi

---

## 5. Từ chối phân chia tiền

**Endpoint:** `POST /api/v1/projects/{projectId}/milestones/{milestoneId}/money-splits/{moneySplitId}/reject`

**Mô tả:** Thành viên được phân chia tiền từ chối phân chia tiền.

**Quyền:** Chỉ người được phân chia tiền (userId trong money split)

**Request Body:**
```json
{
  "rejectionReason": "Số tiền không phù hợp với công việc đã làm"
}
```

**Request Parameters:**
- `rejectionReason` (String, optional): Lý do từ chối

**Validation:**
- Chỉ có thể reject khi status = PENDING
- Không thể reject khi đã APPROVED hoặc REJECTED
- Chỉ người được phân chia tiền mới có quyền reject

**Response (200 OK):**
```json
{
  "code": 200,
  "message": "Từ chối phân chia tiền thành công",
  "result": {
    "id": 1,
    "userId": 123,
    "userName": "Nguyễn Văn A",
    "userEmail": "nguyenvana@example.com",
    "amount": 1000000.00,
    "status": "REJECTED",
    "note": "Phân chia tiền cho công việc thiết kế",
    "rejectionReason": "Số tiền không phù hợp với công việc đã làm",
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T12:00:00"
  }
}
```

**Error Codes:**
- `ACCESS_DENIED`: Money split không thuộc milestone này
- `MONEY_SPLIT_NOT_FOUND`: Không tìm thấy phân chia tiền
- `MONEY_SPLIT_ONLY_MEMBER_CAN_APPROVE`: Chỉ người được phân chia tiền mới có quyền reject
- `MONEY_SPLIT_ALREADY_APPROVED`: Đã được chấp nhận rồi
- `MONEY_SPLIT_ALREADY_REJECTED`: Đã bị từ chối rồi

---

## 6. Lấy chi tiết phân chia tiền

**Endpoint:** `GET /api/v1/projects/{projectId}/milestones/{milestoneId}/money-splits`

**Mô tả:** Lấy danh sách tất cả phân chia tiền và chi phí của milestone, cùng với tổng số tiền.

**Quyền:** Tất cả thành viên của milestone

**Response (200 OK):**
```json
{
  "code": 200,
  "message": "Lấy chi tiết phân chia tiền thành công",
  "result": {
    "moneySplits": [
      {
        "id": 1,
        "userId": 123,
        "userName": "Nguyễn Văn A",
        "userEmail": "nguyenvana@example.com",
        "amount": 1000000.00,
        "status": "APPROVED",
        "note": "Phân chia tiền cho công việc thiết kế",
        "rejectionReason": null,
        "createdAt": "2024-01-15T10:30:00",
        "updatedAt": "2024-01-15T12:00:00",
        "isCurrentUserRecipient": false
      },
      {
        "id": 2,
        "userId": 124,
        "userName": "Trần Thị B",
        "userEmail": "tranthib@example.com",
        "amount": 500000.00,
        "status": "PENDING",
        "note": "Phân chia tiền cho công việc chỉnh sửa",
        "rejectionReason": null,
        "createdAt": "2024-01-15T11:00:00",
        "updatedAt": "2024-01-15T11:00:00",
        "isCurrentUserRecipient": true
      }
    ],
    "expenses": [
      {
        "id": 1,
        "name": "Chi phí mua phần mềm",
        "description": "Mua license Adobe Creative Suite",
        "amount": 2000000.00,
        "createdAt": "2024-01-15T09:00:00",
        "updatedAt": "2024-01-15T09:00:00"
      }
    ],
    "totalSplitAmount": 1500000.00,
    "totalExpenseAmount": 2000000.00,
    "totalAllocated": 3500000.00,
    "milestoneAmount": 10000000.00,
    "remainingAmount": 6500000.00
  }
}
```

**Response Fields:**
- `moneySplits`: Danh sách phân chia tiền, mỗi item có:
  - `id`: ID của phân chia tiền
  - `userId`: ID của user được phân chia tiền
  - `userName`: Tên của user được phân chia tiền
  - `userEmail`: Email của user được phân chia tiền
  - `amount`: Số tiền
  - `status`: Trạng thái (PENDING, APPROVED, REJECTED)
  - `note`: Ghi chú
  - `rejectionReason`: Lý do từ chối (nếu có)
  - `createdAt`: Thời gian tạo
  - `updatedAt`: Thời gian cập nhật
  - `isCurrentUserRecipient`: `true` nếu current user là người được phân chia tiền, `false` nếu không, `null` nếu không có authentication
- `expenses`: Danh sách chi phí
- `totalSplitAmount`: Tổng số tiền đã phân chia
- `totalExpenseAmount`: Tổng số tiền chi phí
- `totalAllocated`: Tổng số tiền đã phân bổ (phân chia + chi phí)
- `milestoneAmount`: Tổng số tiền của milestone
- `remainingAmount`: Số tiền còn lại (milestoneAmount - totalAllocated)

**Lưu ý về `isCurrentUserRecipient`:**
- Field này giúp frontend dễ dàng xác định current user có phải là người được phân chia tiền không
- Nếu `isCurrentUserRecipient === true` và `status === 'PENDING'`, frontend nên hiển thị nút "Chấp nhận" và "Từ chối"
- Nếu `isCurrentUserRecipient === false` hoặc `status !== 'PENDING'`, không hiển thị các nút này

---

## 7. Tạo chi phí

**Endpoint:** `POST /api/v1/projects/{projectId}/milestones/{milestoneId}/expenses`

**Mô tả:** OWNER tạo chi phí cho milestone.

**Quyền:** Chỉ OWNER

**Request Body:**
```json
{
  "name": "Chi phí mua phần mềm",
  "description": "Mua license Adobe Creative Suite",
  "amount": "2000000.00"
}
```

**Request Parameters:**
- `name` (String, required): Tên chi phí
- `description` (String, optional): Mô tả chi phí
- `amount` (String, required): Số tiền

**Validation:**
- Tổng số tiền (phân chia + chi phí) không được vượt quá milestone amount
- Amount phải > 0

**Response (201 Created):**
```json
{
  "code": 201,
  "message": "Tạo chi phí thành công",
  "result": {
    "id": 1,
    "name": "Chi phí mua phần mềm",
    "description": "Mua license Adobe Creative Suite",
    "amount": 2000000.00,
    "createdAt": "2024-01-15T09:00:00",
    "updatedAt": "2024-01-15T09:00:00"
  }
}
```

**Error Codes:**
- `ACCESS_DENIED`: Không có quyền
- `MONEY_SPLIT_TOTAL_EXCEEDS_MILESTONE`: Tổng số tiền vượt quá milestone amount
- `INVALID_PARAMETER_FORMAT`: Amount <= 0 hoặc format không hợp lệ

---

## 8. Cập nhật chi phí

**Endpoint:** `PUT /api/v1/projects/{projectId}/milestones/{milestoneId}/expenses/{expenseId}`

**Mô tả:** OWNER cập nhật thông tin chi phí.

**Quyền:** Chỉ OWNER

**Request Body:**
```json
{
  "name": "Chi phí mua phần mềm (cập nhật)",
  "description": "Mua license Adobe Creative Suite và các plugin",
  "amount": "2500000.00"
}
```

**Response (200 OK):**
```json
{
  "code": 200,
  "message": "Cập nhật chi phí thành công",
  "result": {
    "id": 1,
    "name": "Chi phí mua phần mềm (cập nhật)",
    "description": "Mua license Adobe Creative Suite và các plugin",
    "amount": 2500000.00,
    "createdAt": "2024-01-15T09:00:00",
    "updatedAt": "2024-01-15T10:00:00"
  }
}
```

**Error Codes:**
- `ACCESS_DENIED`: Không có quyền hoặc expense không thuộc milestone này
- `EXPENSE_NOT_FOUND`: Không tìm thấy chi phí
- `MONEY_SPLIT_TOTAL_EXCEEDS_MILESTONE`: Tổng số tiền vượt quá milestone amount

---

## 9. Xóa chi phí

**Endpoint:** `DELETE /api/v1/projects/{projectId}/milestones/{milestoneId}/expenses/{expenseId}`

**Mô tả:** OWNER xóa chi phí.

**Quyền:** Chỉ OWNER

**Response (200 OK):**
```json
{
  "code": 200,
  "message": "Xóa chi phí thành công"
}
```

**Error Codes:**
- `ACCESS_DENIED`: Không có quyền hoặc expense không thuộc milestone này
- `EXPENSE_NOT_FOUND`: Không tìm thấy chi phí

---

## Trạng thái phân chia tiền (MoneySplitStatus)

- `PENDING`: Đang chờ phản hồi từ thành viên
- `APPROVED`: Đã được chấp nhận
- `REJECTED`: Đã bị từ chối

---

## Lưu ý quan trọng

1. **Phân quyền:**
   - OWNER: Tạo, cập nhật, xóa phân chia tiền và chi phí
   - Thành viên được phân chia tiền: Chấp nhận/từ chối phân chia tiền của mình
   - Tất cả thành viên: Xem danh sách phân chia tiền và chi phí

2. **Validation:**
   - Tổng số tiền (phân chia + chi phí) không được vượt quá milestone amount
   - Không thể phân chia tiền cho OWNER hoặc CLIENT
   - Có thể tạo nhiều phân chia tiền cho cùng một user
   - Chỉ không được xóa phân chia tiền khi status = APPROVED (đã được chấp nhận)
   - Có thể xóa phân chia tiền khi status = PENDING hoặc REJECTED

3. **Workflow:**
   - OWNER tạo phân chia tiền → Status = PENDING
   - Thành viên chấp nhận → Status = APPROVED
   - Thành viên từ chối → Status = REJECTED (có thể có lý do)
   - OWNER có thể cập nhật/xóa khi status = PENDING

4. **Email Notification:**
   - Khi tạo/cập nhật phân chia tiền: Gửi email cho thành viên được phân chia
   - Khi chấp nhận/từ chối: Gửi email cho OWNER

