package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContractAddendumPdfFillRequest {

    @NotBlank
    @JsonProperty("addendumNo")
    String addendumNo;

    @NotNull
    @JsonProperty("signDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate signDate;

    @JsonProperty("signPlace")
    String signPlace;

    // BÊN A - Backend tự động lấy từ ContractParty, FE không cần gửi
    @JsonProperty("aName")
    String aName;
    @JsonProperty("aId")
    String aId;
    @JsonProperty("aIdIssueDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate aIdIssueDate;
    @JsonProperty("aIdIssuePlace")
    String aIdIssuePlace;
    @JsonProperty("aAddress")
    String aAddress;
    @JsonProperty("aPhone")
    String aPhone;

    // BÊN B - Backend tự động lấy từ ContractParty, FE không cần gửi
    @JsonProperty("bName")
    String bName;
    @JsonProperty("bId")
    String bId;
    @JsonProperty("bIdIssueDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate bIdIssueDate;
    @JsonProperty("bIdIssuePlace")
    String bIdIssuePlace;
    @JsonProperty("bAddress")
    String bAddress;
    @JsonProperty("bPhone")
    String bPhone;

    @JsonProperty("additional")
    String additional;

    @JsonProperty("numofmoney")
    BigDecimal numOfMoney;

    @JsonProperty("numofedit")
    Integer numOfEdit;

    @JsonProperty("numofrefresh")
    Integer numOfRefresh;

    @NotBlank
    @JsonProperty("title")
    String title;

    @JsonProperty("effectiveDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate effectiveDate;

    @JsonProperty("milestones")
    List<ContractAddendumMilestoneItemRequest> milestones;
}
