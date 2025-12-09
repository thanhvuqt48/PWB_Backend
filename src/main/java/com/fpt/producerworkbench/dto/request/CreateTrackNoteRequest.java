package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO để tạo ghi chú mới
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTrackNoteRequest {

    @NotBlank(message = "Nội dung ghi chú không được để trống")
    private String content;

    @NotNull(message = "Loại phòng không được để trống")
    private RoomType roomType;

    /**
     * Thời điểm trong bài hát (giây) - optional
     */
    private Double timestamp;
}
