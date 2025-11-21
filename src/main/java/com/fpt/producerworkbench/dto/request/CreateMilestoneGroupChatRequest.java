package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMilestoneGroupChatRequest {

    @NotEmpty(message = "Danh sách người tham gia không được để trống")
    @Size(min = 1, message = "Phải có ít nhất 1 người tham gia")
    private List<Long> participantIds;

    @NotBlank(message = "Tên group chat không được để trống")
    @Size(max = 100, message = "Tên group chat không được vượt quá 100 ký tự")
    private String conversationName;
}
