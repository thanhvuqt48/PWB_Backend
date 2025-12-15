package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.*;

/**
 * Request DTO để chuyển tiếp group từ EXTERNAL sang INTERNAL với tùy chọn chỉnh sửa
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForwardBriefGroupRequest {

    /**
     * Nội dung group đã chỉnh sửa (optional)
     * Nếu null, sẽ forward nguyên bản
     * Nếu có, sẽ dùng nội dung này để tạo group mới ở INTERNAL
     */
    @Valid
    @JsonProperty("group")
    private MilestoneBriefGroupRequest group;
}


