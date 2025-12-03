package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * Request để chủ dự án cấp/quản lý quyền download cho track
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TrackDownloadPermissionRequest {

    /**
     * Danh sách user IDs được cấp quyền download track này
     */
    @NotNull(message = "Danh sách user IDs không được để trống")
    private List<Long> userIds;
}

