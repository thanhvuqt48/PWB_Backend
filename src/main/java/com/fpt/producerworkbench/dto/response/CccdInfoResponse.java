package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.dto.vnpt.classify.ClassifyObject;
import com.fpt.producerworkbench.dto.vnpt.compare.CompareFaceObject;
import com.fpt.producerworkbench.dto.vnpt.liveness.CardLivenessObject;
import com.fpt.producerworkbench.dto.vnpt.liveness.FaceLivenessObject;
import com.fpt.producerworkbench.dto.vnpt.ocrid.OcrIdObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CccdInfoResponse {
    private String id;
    private String name;
    private String birthDay;
    private String gender;
    private String originLocation;
    private String recentLocation;
    private String issueDate;
    private String issuePlace;
    private String cardType;
    private ClassifyObject classify;
    private CardLivenessObject cardLiveness;
    private FaceLivenessObject faceLiveness;
    private CompareFaceObject compareFace;

    public static CccdInfoResponse from(Map<String, Object> o) {
        CccdInfoResponse r = new CccdInfoResponse();
        r.setId((String) o.get("id"));
        r.setName((String) o.get("name"));
        r.setBirthDay((String) o.get("birth_day"));
        r.setGender((String) o.get("gender"));
        r.setOriginLocation((String) o.get("origin_location"));
        r.setRecentLocation((String) o.get("recent_location"));
        r.setIssueDate((String) o.get("issue_date"));
        r.setIssuePlace((String) o.get("issue_place"));
        r.setCardType((String) o.get("card_type"));
        return r;
    }

    public static CccdInfoResponse from(OcrIdObject o) {
        CccdInfoResponse r = new CccdInfoResponse();
        r.setId(o.getId());
        r.setName(o.getName());
        r.setBirthDay(o.getBirthDay());
        r.setGender(o.getGender());
        r.setOriginLocation(o.getOriginLocation());
        r.setRecentLocation(o.getRecentLocation());
        r.setIssueDate(o.getIssueDate());
        r.setIssuePlace(o.getIssuePlace());
        r.setCardType(o.getCardType());
        return r;
    }
}
