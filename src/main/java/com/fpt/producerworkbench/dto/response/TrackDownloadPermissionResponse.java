package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.util.Date;
import java.util.List;

/**
 * Response DTO cho danh sách users có quyền download track
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackDownloadPermissionResponse {

    /**
     * Danh sách users có quyền download
     */
    private List<DownloadPermissionUser> users;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DownloadPermissionUser {
        private Long userId;
        private String userName;
        private String userEmail;
        private String userAvatarUrl;
        
        /**
         * User đã cấp quyền này (thường là chủ dự án)
         */
        private Long grantedByUserId;
        private String grantedByUserName;
        
        /**
         * Thời gian được cấp quyền
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private Date grantedAt;
    }
}




