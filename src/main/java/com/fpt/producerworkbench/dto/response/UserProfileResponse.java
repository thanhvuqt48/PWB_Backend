package com.fpt.producerworkbench.dto.response;

import jakarta.persistence.Column;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileResponse {

    String email;

    String firstName;

    String lastName;

    String phoneNumber;

    LocalDate dateOfBirth;

    String avatarUrl;

    String location;

    String role;

    String cccdNumber;

    String cccdIssueDate;

    String cccdIssuePlace;

    Boolean isVerified;

}

