package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractCreateRequest {

    @NotBlank(message = "Chi tiết hợp đồng không được để trống")
    private String contractDetails;

    @NotNull(message = "Tổng giá trị không được để trống")
    @Positive(message = "Tổng giá trị phải là số dương")
    private BigDecimal totalAmount;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentType paymentType;

    @Valid
    private List<MilestoneRequest> milestones;
}