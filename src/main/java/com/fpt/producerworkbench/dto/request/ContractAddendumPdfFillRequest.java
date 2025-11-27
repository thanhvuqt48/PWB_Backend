package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Setter @Getter @Builder
@AllArgsConstructor @NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContractAddendumPdfFillRequest {


    @NotBlank @JsonProperty("addendumNo")
    String addendumNo;

    @NotNull
    @JsonProperty("signDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate signDate;

    @JsonProperty("signPlace")
    String signPlace;

    // BÊN A
    @NotBlank @JsonProperty("aName")  String aName;
    @NotBlank @JsonProperty("aId")    String aId;
    @NotNull  @JsonProperty("aIdIssueDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate aIdIssueDate;
    @NotBlank @JsonProperty("aIdIssuePlace") String aIdIssuePlace;
    @NotBlank @JsonProperty("aAddress")      String aAddress;
    @JsonProperty("aPhone")          String aPhone;


    // BÊN B
    @NotBlank @JsonProperty("bName")  String bName;
    @NotBlank @JsonProperty("bId")    String bId;
    @NotNull  @JsonProperty("bIdIssueDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate bIdIssueDate;
    @NotBlank @JsonProperty("bIdIssuePlace") String bIdIssuePlace;
    @NotBlank @JsonProperty("bAddress")      String bAddress;
    @JsonProperty("bPhone")          String bPhone;



    @JsonProperty("additional")
    String additional;

    @JsonProperty("numofmoney")
    BigDecimal numOfMoney;

    @JsonProperty("numofedit")
    Integer numOfEdit;

    @JsonProperty("numofrefresh")
    Integer numOfRefresh;

    @NotBlank @JsonProperty("title")
    String title;

    @JsonProperty("effectiveDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate effectiveDate;

    @JsonProperty("milestones")
    List<ContractAddendumMilestoneItemRequest> milestones;
}
