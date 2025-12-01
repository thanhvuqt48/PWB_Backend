package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PersonalProjectUpdateRequest {
    private Long id;

    @NotBlank(message = "Tiêu đề dự án không được để trống")
    private String title;

    private String description;

    @NotNull(message = "Năm phát hành không được để trống")
    @Min(value = 1900, message = "Năm phát hành phải từ 1900 trở lên")
    private Integer releaseYear;
}
