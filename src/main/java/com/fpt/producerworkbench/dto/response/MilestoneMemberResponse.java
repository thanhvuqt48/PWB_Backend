package com.fpt.producerworkbench.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneMemberResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String avatarUrl; // URL avatar của người dùng
    private String description; // Mô tả vai trò, ví dụ: "Nghệ sĩ Guitar", "Producer", etc.
    private String role; // OWNER, CLIENT, COLLABORATOR, OBSERVER
    private Boolean isAnonymous; // Đánh dấu thành viên ẩn danh
}


