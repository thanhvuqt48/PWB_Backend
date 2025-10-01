package com.fpt.producerworkbench.dto.request;


import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubscriptionPlanRequest {
    @NotBlank(message = "Tên gói không được để trống")
    @Size(max = 255, message = "Tên gói không được vượt quá 255 ký tự")
    String name;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá phải lớn hơn 0")
    @Digits(integer = 8, fraction = 2, message = "Giá không hợp lệ")
    BigDecimal price;

    @NotBlank(message = "Đơn vị tiền tệ không được để trống")
    @Size(min = 3, max = 3, message = "Đơn vị tiền tệ phải có đúng 3 ký tự")
    String currency;

    @Min(value = 1, message = "Thời gian gói phải ít nhất 1 ngày")
    @Max(value = 3650, message = "Thời gian gói không được vượt quá 10 năm")
    int durationDays;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    String description;

}
