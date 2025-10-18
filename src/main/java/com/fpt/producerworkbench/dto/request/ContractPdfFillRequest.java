//
//package com.fpt.producerworkbench.dto.request;
//
//import com.fasterxml.jackson.annotation.JsonFormat;
//import com.fasterxml.jackson.annotation.JsonProperty;
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
//    @JsonProperty("signDate")
//    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
//    LocalDate signDate;
//
//    @JsonProperty("signPlace") String signPlace;
//
//    // Bên A
//    @JsonProperty("aName")  String aName;
//    @JsonProperty("aCccd")  String aCccd;
//
//    @JsonProperty("aCccdIssueDate")
//    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
//    LocalDate aCccdIssueDate;
//
//    @JsonProperty("aCccdIssuePlace") String aCccdIssuePlace;
//    @JsonProperty("aAddress")        String aAddress;
//    @JsonProperty("aPhone")          String aPhone;
//    @JsonProperty("aRepresentative") String aRepresentative;
//    @JsonProperty("aTitle")          String aTitle;
//    @JsonProperty("aPoANo")          String aPoANo;
//
//    // Bên B
//    @JsonProperty("bName")  String bName;
//    @JsonProperty("bCccd")  String bCccd;
//
//    @JsonProperty("bCccdIssueDate")
//    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
//    LocalDate bCccdIssueDate;
//
//    @JsonProperty("bCccdIssuePlace") String bCccdIssuePlace;
//    @JsonProperty("bAddress")        String bAddress;
//    @JsonProperty("bPhone")          String bPhone;
//    @JsonProperty("bRepresentative") String bRepresentative;
//    @JsonProperty("bTitle")          String bTitle;
//    @JsonProperty("bPoANo")          String bPoANo;
//
//    // Hạng mục 1–3
//    @JsonProperty("line1Item")  String line1Item;
//    @JsonProperty("line1Unit")  String line1Unit;
//    @JsonProperty("line1Qty")   Integer line1Qty;
//    @JsonProperty("line1Price") String line1Price;
//    @JsonProperty("line1Amount")String line1Amount;
//
//    @JsonProperty("line2Item")  String line2Item;
//    @JsonProperty("line2Unit")  String line2Unit;
//    @JsonProperty("line2Qty")   Integer line2Qty;
//    @JsonProperty("line2Price") String line2Price;
//    @JsonProperty("line2Amount")String line2Amount;
//
//    @JsonProperty("line3Item")  String line3Item;
//    @JsonProperty("line3Unit")  String line3Unit;
//    @JsonProperty("line3Qty")   Integer line3Qty;
//    @JsonProperty("line3Price") String line3Price;
//    @JsonProperty("line3Amount")String line3Amount;
//
//    // Tổng hợp & giá trị HĐ
//    @JsonProperty("sumAmount")            String sumAmount;
//    @JsonProperty("vat8")                 String vat8;
//    @JsonProperty("grandTotal")           String grandTotal;
//    @JsonProperty("contractPriceText")    String contractPriceText;
//    @JsonProperty("contractPriceInWords") String contractPriceInWords;
//
//    // Thanh toán
//    @JsonProperty("payOnce")      Boolean payOnce;
//    @JsonProperty("payMilestone") Boolean payMilestone;
//
//    @JsonProperty("ms1Amount") String ms1Amount;
//    @JsonProperty("ms1Percent")String ms1Percent;
//    @JsonProperty("ms2Amount") String ms2Amount;
//    @JsonProperty("ms2Percent")String ms2Percent;
//    @JsonProperty("ms3Amount") String ms3Amount;
//    @JsonProperty("ms3Percent")String ms3Percent;
//
//    @JsonProperty("milestones") private List<MilestoneReq> milestones;
//}
//
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

    @JsonProperty("payOnce")      Boolean payOnce;
    @JsonProperty("payMilestone") Boolean payMilestone;

    @JsonProperty("milestones") private List<MilestoneRequest> milestones;

    @NotBlank
    @JsonProperty("percent")
    String percent;

    @JsonProperty("fpEditAmount")
    Integer fpEditAmount;
}
