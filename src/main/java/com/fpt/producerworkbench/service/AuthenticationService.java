package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.AuthenticationRequest;
import com.fpt.producerworkbench.dto.request.IntrospectRequest;
import com.fpt.producerworkbench.dto.request.LogoutRequest;
import com.fpt.producerworkbench.dto.request.RefreshTokenRequest;
import com.fpt.producerworkbench.dto.response.AuthenticationResponse;
import com.fpt.producerworkbench.dto.response.IntrospectResponse;

public interface AuthenticationService {

    AuthenticationResponse authenticate(AuthenticationRequest request);
    IntrospectResponse introspect(IntrospectRequest request);
    void logout(LogoutRequest request);
    AuthenticationResponse refreshToken(RefreshTokenRequest request);

}
