package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.exception.ExpiredTokenException;
import com.fpt.producerworkbench.exception.InvalidTokenException;
import com.fpt.producerworkbench.repository.InvalidatedTokenRepository;
import com.fpt.producerworkbench.service.JwtService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class JwtServiceImpl implements JwtService{

    @NonFinal
    @Value("${jwt.secret-key}")
    protected String secretKey;

    @NonFinal
    @Value("${jwt.valid-duration}")
    protected long validDuration;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    protected long refreshableDuration;

    InvalidatedTokenRepository invalidatedTokenRepository;

    public String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getEmail())
                .issuer("producerworkbench.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(validDuration, ChronoUnit.HOURS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", buildScope(user))
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(secretKey.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new AppException(ErrorCode.TOKEN_CREATION_FAIL);
        }
    }


    public SignedJWT verifyToken(String token, boolean isRefresh) {
        if (token == null || token.trim().isEmpty()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        JWSVerifier verifier = null;
        try {
            verifier = new MACVerifier(secretKey.getBytes());

            SignedJWT signedJWT = SignedJWT.parse(token);

            Date expiryTime = (isRefresh)
                    ? new Date(signedJWT
                    .getJWTClaimsSet()
                    .getIssueTime()
                    .toInstant()
                    .plus(refreshableDuration, ChronoUnit.DAYS)
                    .toEpochMilli())
                    : signedJWT.getJWTClaimsSet().getExpirationTime();

            if (expiryTime.before(new Date())) {
                throw new ExpiredTokenException();
            }

            var verified = signedJWT.verify(verifier);
            if (!verified) throw new InvalidTokenException();

            if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
                throw new InvalidTokenException();

            return signedJWT;

        } catch (JOSEException | ParseException e) {
            throw new RuntimeException(e);
        }

    }

    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");

        Optional.ofNullable(user.getRole()).ifPresent(role -> stringJoiner.add(role.toString()));
        return stringJoiner.toString();
    }
}
