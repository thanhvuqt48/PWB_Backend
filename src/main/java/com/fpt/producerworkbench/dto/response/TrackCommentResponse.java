package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fpt.producerworkbench.common.CommentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Date;
import java.util.List;

/**
 * Response chứa thông tin comment trên track
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrackCommentResponse {

    /**
     * ID của comment
     */
    Long id;

    /**
     * ID của track
     */
    Long trackId;

    /**
     * Thông tin user tạo comment
     */
    UserBasicInfo user;

    /**
     * Nội dung comment
     */
    String content;

    /**
     * Timestamp trong track (giây)
     */
    Integer timestamp;

    /**
     * Trạng thái xử lý
     */
    CommentStatus status;

    /**
     * ID của comment cha (nếu là reply)
     */
    Long parentCommentId;

    /**
     * Số lượng reply
     */
    Long replyCount;

    /**
     * Danh sách reply (chỉ load khi cần)
     */
    List<TrackCommentResponse> replies;

    /**
     * Thời gian tạo
     */
    Date createdAt;

    /**
     * Thời gian cập nhật
     */
    Date updatedAt;

    /**
     * Nested class chứa thông tin cơ bản của user
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class UserBasicInfo {
        Long id;
        String firstName;
        String lastName;
        String fullName;
        String email;
        String avatarUrl;
    }
}



