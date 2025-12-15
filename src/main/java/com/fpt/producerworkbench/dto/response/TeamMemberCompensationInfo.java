package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Thông tin đền bù cho từng thành viên trong Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberCompensationInfo {
    
    private Long userId;
    private String userName;
    private String userEmail;
    private String avatarUrl;
    private String description; // Mô tả vai trò từ milestone_members
    
    private Long milestoneId;
    private String milestoneName;
    private BigDecimal amount; // Số tiền đền bù (gross, chưa trừ thuế)
}

