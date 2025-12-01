package com.fpt.producerworkbench.dto.vnpt.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TokenExchangeResponse implements Serializable {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("refresh_token")
    private String refreshToken;

    private String sub;

    @JsonProperty("user_name")
    private String username;

    private String scope;

    private String iss;

    private String name;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("uuid_account")
    private String uuidAccount;

    private String jti;

}
