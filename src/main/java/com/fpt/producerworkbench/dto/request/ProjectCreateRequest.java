package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.ProjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectCreateRequest {

    @NotBlank(message = "Tiêu đề dự án không được bỏ trống")
    private String title;

    private String description;

    @NotNull(message = "Phải chỉ rõ loại dự án (CÁ NHÂN hoặc HỢP TÁC)")
    private ProjectType type;
}
