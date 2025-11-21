package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.CommentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request để cập nhật trạng thái của comment
 * Chỉ track owner mới có quyền thực hiện
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TrackCommentStatusUpdateRequest {

    /**
     * Trạng thái mới của comment
     */
    @NotNull(message = "Trạng thái không được để trống")
    CommentStatus status;
}



