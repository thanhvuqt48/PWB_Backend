package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.MilestoneStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneDetailResponse {

    private Long id;
    private Long contractId;
    private String title;
    private String description;
    private BigDecimal amount;
    private LocalDate dueDate;
    private MilestoneStatus status;
    private Integer editCount;
    private Integer productCount;
    private Integer sequence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<MilestoneMemberResponse> members;
}


