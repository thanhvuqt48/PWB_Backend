package com.fpt.producerworkbench.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddMilestoneMemberRequest {

    @NotNull(message = "Danh sách thành viên không được để trống")
    @NotEmpty(message = "Danh sách thành viên không được để trống")
    @Valid
    private List<MilestoneMemberRequestItem> members;
}

