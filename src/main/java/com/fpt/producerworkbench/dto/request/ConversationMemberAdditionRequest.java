package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Getter
public class ConversationMemberAdditionRequest {

    @NotEmpty(message = "Danh sách thành viên không được để trống")
    private List<Long> memberIds;
}






