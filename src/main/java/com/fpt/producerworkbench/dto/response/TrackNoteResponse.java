package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Response DTO cho ghi chú track
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackNoteResponse {

    private Long id;
    
    private Long trackId;
    
    private String content;
    
    private RoomType roomType;
    
    // Thông tin người tạo
    private Long userId;
    private String userName;
    private String userAvatar;
    
    /**
     * Thời điểm trong bài hát (giây)
     */
    private Double timestamp;
    
    private Date createdAt;
    private Date updatedAt;
}
