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
public class UpdatePersonalProfileRequest {

    @Size(max = 100, message = "First name must not exceed 100 characters")
    String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    String lastName;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    String phoneNumber;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @DateOfBirth(message = "INVALID_DOB")
    LocalDate dateOfBirth;

    @Size(max = 200, message = "Location must not exceed 200 characters")
    String location;
}

