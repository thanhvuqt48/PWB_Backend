package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request để tạo comment mới trên track
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TrackCommentCreateRequest {

    /**
     * Nội dung comment
     */
    @NotBlank(message = "Nội dung comment không được để trống")
    @Size(max = 2000, message = "Nội dung comment không được vượt quá 2000 ký tự")
    String content;

    /**
     * Timestamp trong track (giây) - vị trí thời gian comment
     * Có thể null nếu comment không gắn với thời điểm cụ thể
     */
    Integer timestamp;

    /**
     * ID của comment cha (nếu đây là reply)
     * Null nếu đây là comment gốc
     */
    Long parentCommentId;
}



