package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.exception.validation.constraint.DateOfBirth;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {

    String email;

    @Size(min = 6, message = "INVALID_PASSWORD")
    String passwordHash;

    String firstName;

    String lastName;

    @DateTimeFormat(pattern = "yyyy/MM/dd")
    @DateOfBirth(message = "INVALID_DOB")
    LocalDate dateOfBirth;
}
