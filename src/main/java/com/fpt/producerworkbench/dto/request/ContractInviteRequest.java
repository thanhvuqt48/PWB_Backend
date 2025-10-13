package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.SigningMode;
import com.fpt.producerworkbench.common.SigningOrderType;
import lombok.*;

import java.util.List;


@Setter
@Getter
@Builder @NoArgsConstructor @AllArgsConstructor
public class ContractInviteRequest {
    private String pdfBase64;
    private SigningMode signingMode;
    private SigningOrderType signingOrder;
    private Boolean useFieldInvite;

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Signer {
        private String fullName;
        private String email;
        private Integer order;
        private String roleId;
        private String roleName;
    }
    private List<Signer> signers;
}
