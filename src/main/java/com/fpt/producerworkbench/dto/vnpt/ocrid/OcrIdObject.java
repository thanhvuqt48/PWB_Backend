package com.fpt.producerworkbench.dto.vnpt.ocrid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OcrIdObject {

    private String id;

    private String name;

    @JsonProperty("card_type")
    private String cardType;

    @JsonProperty("birth_day")
    private String birthDay;

    private String gender;

    @JsonProperty("origin_location")
    private String originLocation;

    @JsonProperty("recent_location")
    private String recentLocation;

    @JsonProperty("expire_warning")
    private String expireWarning;


    @JsonProperty("issue_place")
    private String issuePlace;

    @JsonProperty("issue_date")
    private String issueDate;

    @JsonProperty("back_expire_warning")
    private String backExpireWarning;

}
