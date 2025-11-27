package com.fpt.producerworkbench.dto.request;

import lombok.*;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CccdRequest {

    private String cccdNumber;

    private String cccdFullName;

    private String cccdBirthDay;

    private String cccdGender;

    private String cccdOriginLocation;

    private String cccdRecentLocation;

    private String cccdIssueDate;

    private String cccdIssuePlace;
}
