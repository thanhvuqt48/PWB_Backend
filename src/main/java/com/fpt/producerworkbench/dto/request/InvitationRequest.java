package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvitationRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotNull(message = "Vai trò phải được chỉ định")
    private ProjectRole role;

    @AssertTrue(message = "Vai trò được mời không hợp lệ. Chỉ có thể là KHÁCH HÀNG, NGƯỜI QUAN SÁT hoặc CỘNG TÁC.")
    private boolean isValidInvitationRole() {
        return role == ProjectRole.CLIENT || role == ProjectRole.OBSERVER || role == ProjectRole.COLLABORATOR;
    }

    // Chỉ áp dụng khi role == COLLABORATOR. Mặc định là công khai nếu không truyền.
    private Boolean anonymous;

    @AssertTrue(message = "Tùy chọn ẩn danh chỉ áp dụng cho vai trò CỘNG TÁC.")
    private boolean isAnonymousOptionValid() {
        if (role == null) return true; // để các validator khác xử lý null
        return role == ProjectRole.COLLABORATOR || anonymous == null || !anonymous;
    }
}