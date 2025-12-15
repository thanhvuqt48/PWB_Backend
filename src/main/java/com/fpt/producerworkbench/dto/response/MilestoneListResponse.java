package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fpt.producerworkbench.common.MilestoneStatus;
import com.fpt.producerworkbench.common.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneListResponse {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("status")
    private MilestoneStatus status;

    @JsonProperty("paymentStatus")
    private PaymentStatus paymentStatus;
    
    @JsonProperty("sequence")
    private Integer sequence;
    
    @JsonProperty("productCount")
    private Integer productCount;

    @JsonProperty("editCount")
    private Integer editCount;

    // Số tiền của cột mốc
    @JsonProperty("amount")
    private BigDecimal amount;
    
    // Tổng số lượng sản phẩm theo hợp đồng của dự án
    @JsonProperty("contractProductCount")
    private Integer contractProductCount;

    // Tổng số lượt chỉnh sửa theo hợp đồng của dự án
    @JsonProperty("contractFpEditCount")
    private Integer contractFpEditCount;

    // Tổng số tiền của hợp đồng (toàn dự án)
    @JsonProperty("contractTotalAmount")
    private BigDecimal contractTotalAmount;
    
    @JsonProperty("projectTitle")
    private String projectTitle;
    
    @JsonProperty("createdAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date createdAt;
    
    @JsonProperty("updatedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date updatedAt;
}

