package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MilestoneMemberRequestItem {

    @NotNull(message = "User ID không được để trống")
    private Long userId;

    private String description; // Mô tả vai trò, ví dụ: "Nghệ sĩ Guitar", "Producer", etc.
}

