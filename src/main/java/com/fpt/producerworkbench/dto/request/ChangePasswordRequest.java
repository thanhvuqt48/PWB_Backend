package com.fpt.producerworkbench.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordRequest {

    @Size(min = 6, message = "INVALID_PASSWORD")
    String currentPassword;

    @Size(min = 6, message = "INVALID_PASSWORD")
    String newPassword;

    @NotBlank(message = "Confirm password is required")
    String confirmPassword;

}
