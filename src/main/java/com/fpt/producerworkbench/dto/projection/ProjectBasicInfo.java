package com.fpt.producerworkbench.dto.projection;

/**
 * Projection để lấy thông tin cơ bản của Project mà không load các collection (liveSessions).
 * Dùng trong các trường hợp chỉ cần đọc data, không cần modify entity.
 */
public interface ProjectBasicInfo {
    Long getId();
    String getTitle();
    Long getCreatorId();
    String getCreatorEmail();
    String getCreatorFullName();
    Long getClientId();
}
