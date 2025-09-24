package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.entity.User;
import com.nimbusds.jwt.SignedJWT;

public interface JwtService {
    String generateToken(User user);
    SignedJWT verifyToken(String token, boolean isRefresh);
}
