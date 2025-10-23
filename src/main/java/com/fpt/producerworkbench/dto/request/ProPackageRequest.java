package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.entity.ProPackage;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProPackageRequest {

    @NotBlank(message = "Tên gói không được để trống")
    @Size(max = 255, message = "Tên gói không được vượt quá 255 ký tự")
    String name;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    String description;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá phải lớn hơn 0")
    @Digits(integer = 8, fraction = 2, message = "Giá không hợp lệ")
    BigDecimal price;

    @NotNull(message = "Loại gói không được để trống")
    ProPackage.ProPackageType packageType;

    @NotNull(message = "Trạng thái hoạt động không được để trống")
    @Builder.Default
    Boolean isActive = true;
}
