//package com.fpt.producerworkbench.dto.request;
//
//import com.fasterxml.jackson.annotation.JsonFormat;
//import com.fasterxml.jackson.annotation.JsonProperty;
//import jakarta.validation.constraints.*;
//import lombok.*;
//import lombok.experimental.FieldDefaults;
//
//import java.time.LocalDate;
//import java.util.List;
//
//@Setter @Getter @Builder
//@AllArgsConstructor @NoArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE)
//public class ContractPdfFillRequest {
//
//    @JsonProperty("contractNo") String contractNo;
//
//    @NotNull
//    @JsonProperty("signDate")
//    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
//    LocalDate signDate;
//
//    @NotBlank @JsonProperty("aName")  String aName;
//    @NotBlank @JsonProperty("aCccd")  String aCccd;
//
//    @NotNull
//    @JsonProperty("aCccdIssueDate")
//    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
//    LocalDate aCccdIssueDate;
//
//    @NotBlank @JsonProperty("aCccdIssuePlace") String aCccdIssuePlace;
//    @NotBlank @JsonProperty("aAddress")        String aAddress;
//
//    @NotBlank @JsonProperty("bName")  String bName;
//    @NotBlank @JsonProperty("bCccd")  String bCccd;
//
//    @NotNull
//    @JsonProperty("bCccdIssueDate")
//    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
//    LocalDate bCccdIssueDate;
//
//    @NotBlank @JsonProperty("bCccdIssuePlace") String bCccdIssuePlace;
//    @NotBlank @JsonProperty("bAddress")        String bAddress;
//
//    @NotBlank @JsonProperty("line1Item")   String line1Item;
//    @NotBlank @JsonProperty("line1Unit")   String line1Unit;
//    @NotNull  @Positive @JsonProperty("line1Qty") Integer line1Qty;
//    @NotBlank @JsonProperty("line1Price")  String line1Price;
//    @NotBlank @JsonProperty("line1Amount") String line1Amount;
//
//    @JsonProperty("signPlace") String signPlace;
//
//    @JsonProperty("aPhone")          String aPhone;
//    @JsonProperty("aRepresentative") String aRepresentative;
//    @JsonProperty("aTitle")          String aTitle;
//    @JsonProperty("aPoANo")          String aPoANo;
//
//    @JsonProperty("bPhone")          String bPhone;
//    @JsonProperty("bRepresentative") String bRepresentative;
//    @JsonProperty("bTitle")          String bTitle;
//    @JsonProperty("bPoANo")          String bPoANo;
//
//    @JsonProperty("line2Item")   String line2Item;
//    @JsonProperty("line2Unit")   String line2Unit;
//    @JsonProperty("line2Qty")    Integer line2Qty;
//    @JsonProperty("line2Price")  String line2Price;
//    @JsonProperty("line2Amount") String line2Amount;
//
//    @JsonProperty("line3Item")   String line3Item;
//    @JsonProperty("line3Unit")   String line3Unit;
//    @JsonProperty("line3Qty")    Integer line3Qty;
//    @JsonProperty("line3Price")  String line3Price;
//    @JsonProperty("line3Amount") String line3Amount;
//
//    @JsonProperty("payOnce")      Boolean payOnce;
//    @JsonProperty("payMilestone") Boolean payMilestone;
//
//    @JsonProperty("milestones") private List<MilestoneRequest> milestones;
//
//    @NotBlank
//    @JsonProperty("percent")
//    String percent;
//
//    @JsonProperty("fpEditAmount")
//    Integer fpEditAmount;
//}

package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;

@Setter @Getter @Builder
@AllArgsConstructor @NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContractPdfFillRequest {

    @JsonProperty("contractNo") String contractNo;

    @NotNull
    @JsonProperty("signDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate signDate;

    @NotBlank @JsonProperty("aName")  String aName;
    @NotBlank @JsonProperty("aCccd")  String aCccd;

    @NotNull
    @JsonProperty("aCccdIssueDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate aCccdIssueDate;

    @NotBlank @JsonProperty("aCccdIssuePlace") String aCccdIssuePlace;
    @NotBlank @JsonProperty("aAddress")        String aAddress;

    @NotBlank @JsonProperty("bName")  String bName;
    @NotBlank @JsonProperty("bCccd")  String bCccd;

    @NotNull
    @JsonProperty("bCccdIssueDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate bCccdIssueDate;

    @NotBlank @JsonProperty("bCccdIssuePlace") String bCccdIssuePlace;
    @NotBlank @JsonProperty("bAddress")        String bAddress;

    @NotBlank @JsonProperty("line1Item")   String line1Item;
    @NotBlank @JsonProperty("line1Unit")   String line1Unit;
    @NotNull  @Positive @JsonProperty("line1Qty") Integer line1Qty;
    @NotBlank @JsonProperty("line1Price")  String line1Price;
    @NotBlank @JsonProperty("line1Amount") String line1Amount;

    @JsonProperty("signPlace") String signPlace;

    @JsonProperty("aPhone")          String aPhone;
    @JsonProperty("aRepresentative") String aRepresentative;
    @JsonProperty("aTitle")          String aTitle;
    @JsonProperty("aPoANo")          String aPoANo;

    @JsonProperty("bPhone")          String bPhone;
    @JsonProperty("bRepresentative") String bRepresentative;
    @JsonProperty("bTitle")          String bTitle;
    @JsonProperty("bPoANo")          String bPoANo;

    @JsonProperty("line2Item")   String line2Item;
    @JsonProperty("line2Unit")   String line2Unit;
    @JsonProperty("line2Qty")    Integer line2Qty;
    @JsonProperty("line2Price")  String line2Price;
    @JsonProperty("line2Amount") String line2Amount;

    @JsonProperty("line3Item")   String line3Item;
    @JsonProperty("line3Unit")   String line3Unit;
    @JsonProperty("line3Qty")    Integer line3Qty;
    @JsonProperty("line3Price")  String line3Price;
    @JsonProperty("line3Amount") String line3Amount;

    @JsonProperty("line4Item")   String line4Item;
    @JsonProperty("line4Unit")   String line4Unit;
    @JsonProperty("line4Qty")    Integer line4Qty;
    @JsonProperty("line4Price")  String line4Price;
    @JsonProperty("line4Amount") String line4Amount;

    @JsonProperty("line5Item")   String line5Item;
    @JsonProperty("line5Unit")   String line5Unit;
    @JsonProperty("line5Qty")    Integer line5Qty;
    @JsonProperty("line5Price")  String line5Price;
    @JsonProperty("line5Amount") String line5Amount;

    @JsonProperty("line6Item")   String line6Item;
    @JsonProperty("line6Unit")   String line6Unit;
    @JsonProperty("line6Qty")    Integer line6Qty;
    @JsonProperty("line6Price")  String line6Price;
    @JsonProperty("line6Amount") String line6Amount;

    @JsonProperty("line7Item")   String line7Item;
    @JsonProperty("line7Unit")   String line7Unit;
    @JsonProperty("line7Qty")    Integer line7Qty;
    @JsonProperty("line7Price")  String line7Price;
    @JsonProperty("line7Amount") String line7Amount;

    @JsonProperty("line8Item")   String line8Item;
    @JsonProperty("line8Unit")   String line8Unit;
    @JsonProperty("line8Qty")    Integer line8Qty;
    @JsonProperty("line8Price")  String line8Price;
    @JsonProperty("line8Amount") String line8Amount;

    @JsonProperty("line9Item")   String line9Item;
    @JsonProperty("line9Unit")   String line9Unit;
    @JsonProperty("line9Qty")    Integer line9Qty;
    @JsonProperty("line9Price")  String line9Price;
    @JsonProperty("line9Amount") String line9Amount;

    @JsonProperty("line10Item")   String line10Item;
    @JsonProperty("line10Unit")   String line10Unit;
    @JsonProperty("line10Qty")    Integer line10Qty;
    @JsonProperty("line10Price")  String line10Price;
    @JsonProperty("line10Amount") String line10Amount;

    @JsonProperty("payOnce")      Boolean payOnce;
    @JsonProperty("payMilestone") Boolean payMilestone;

    @JsonProperty("milestones") private List<MilestoneRequest> milestones;

    @NotBlank
    @JsonProperty("percent")
    String percent;

    @JsonProperty("fpEditAmount")
    Integer fpEditAmount;
}
