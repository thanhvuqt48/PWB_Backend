package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PartyBInfoResponse {

    @JsonProperty("bName")
    private String bName;

    @JsonProperty("bCccd")
    private String bCccd;

    @JsonProperty("bCccdIssueDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate bCccdIssueDate;

    @JsonProperty("bCccdIssuePlace")
    private String bCccdIssuePlace;

    @JsonProperty("bAddress")
    private String bAddress;

    @JsonProperty("bPhone")
    private String bPhone;

    @JsonProperty("isVerified")
    private Boolean isVerified;
}

