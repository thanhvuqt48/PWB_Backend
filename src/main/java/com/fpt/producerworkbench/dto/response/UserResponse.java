package com.fpt.producerworkbench.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {

    String email;

    String firstName;

    String lastName;

    LocalDate dateOfBirth;

    String role;

}
