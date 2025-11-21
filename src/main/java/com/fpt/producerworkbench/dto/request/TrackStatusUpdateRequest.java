package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.TrackStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request DTO để chủ dự án phê duyệt/từ chối trạng thái track
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackStatusUpdateRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private TrackStatus status;

    /**
     * Lý do phê duyệt/từ chối (optional)
     * Ví dụ: "Track đã đạt yêu cầu chất lượng", "Cần chỉnh sửa lại phần intro"
     */
    private String reason;
}

