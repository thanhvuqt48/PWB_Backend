package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response cho API kiểm tra quyền note
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotePermissionResponse {
    
    /**
     * User có thể note không
     */
    private boolean canNote;
    
    /**
     * User có phải Host (owner dự án) không
     */
    private boolean isHost;
    
    /**
     * User có phải Client (khách hàng) không
     */
    private boolean isClient;
}
