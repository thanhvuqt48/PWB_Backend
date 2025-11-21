package com.fpt.producerworkbench.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Response chứa thống kê comment của track
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TrackCommentStatisticsResponse {

    /**
     * ID của track
     */
    Long trackId;

    /**
     * Tổng số comment
     */
    Long totalComments;

    /**
     * Số comment đang chờ xử lý
     */
    Long pendingComments;

    /**
     * Số comment đang được xử lý
     */
    Long inProgressComments;

    /**
     * Số comment đã xử lý xong
     */
    Long resolvedComments;
}



