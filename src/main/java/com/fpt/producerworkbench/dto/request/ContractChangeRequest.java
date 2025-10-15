package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContractChangeRequest {
    @NotBlank(message = "Nội dung yêu cầu chỉnh sửa không được để trống")
    private String comments;
}