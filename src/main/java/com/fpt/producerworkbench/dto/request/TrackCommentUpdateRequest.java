package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request để cập nhật nội dung comment
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TrackCommentUpdateRequest {

    /**
     * Nội dung comment mới
     */
    @NotBlank(message = "Nội dung comment không được để trống")
    @Size(max = 2000, message = "Nội dung comment không được vượt quá 2000 ký tự")
    String content;
}



