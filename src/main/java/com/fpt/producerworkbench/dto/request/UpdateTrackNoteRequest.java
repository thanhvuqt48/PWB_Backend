package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO để cập nhật ghi chú
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTrackNoteRequest {

    @NotBlank(message = "Nội dung ghi chú không được để trống")
    private String content;
}
