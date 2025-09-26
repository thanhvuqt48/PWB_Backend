package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.request.AuthenticationRequest;
import com.fpt.producerworkbench.dto.request.IntrospectRequest;
import com.fpt.producerworkbench.dto.request.LogoutRequest;
import com.fpt.producerworkbench.dto.request.RefreshTokenRequest;
import com.fpt.producerworkbench.dto.response.AuthenticationResponse;
import com.fpt.producerworkbench.dto.response.IntrospectResponse;
import com.fpt.producerworkbench.entity.InvalidatedToken;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.InvalidatedTokenRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.AuthenticationService;
import com.fpt.producerworkbench.service.JwtService;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Date;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    UserRepository userRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;
    JwtService jwtService;
    AuthenticationManager authenticationManager;

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        var user = (User) authentication.getPrincipal();
        var token = jwtService.generateToken(user);

        log.info("User {} logged in successfully", user.getEmail());

        return AuthenticationResponse.builder().token(token).authenticated(true).build();
    }

    public void logout(LogoutRequest request) {
        try {
            var signToken = jwtService.verifyToken(request.getToken(), true);

            String jit = signToken.getJWTClaimsSet().getJWTID();
            Date expiryTime = signToken.getJWTClaimsSet().getExpirationTime();

            if (!invalidatedTokenRepository.existsById(jit)) {
                InvalidatedToken invalidatedToken = InvalidatedToken.builder()
                        .id(jit)
                        .expiryTime(expiryTime)
                        .build();

                invalidatedTokenRepository.save(invalidatedToken);
            } else {
                log.info("Token has already been invalidated");
            }

        } catch (AppException exception) {
            log.info("Token already expired or invalid");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        var signJWT = jwtService.verifyToken(request.getToken(), true);

        String jit = null;
        try {
            jit = signJWT.getJWTClaimsSet().getJWTID();

            var expiryTime = signJWT.getJWTClaimsSet().getExpirationTime();

            InvalidatedToken invalidatedToken =
                    InvalidatedToken.builder().id(jit).expiryTime(expiryTime).build();
            invalidatedTokenRepository.save(invalidatedToken);

            var email = signJWT.getJWTClaimsSet().getSubject();
            var user = userRepository
                    .findByEmail(email)
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

            var token = jwtService.generateToken(user);

            return AuthenticationResponse.builder().token(token).authenticated(true).build();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

    }

    public IntrospectResponse introspect(IntrospectRequest request) {
        var token = request.getToken();

        boolean isValid = true;
        String scope = null;
        try {
            SignedJWT signedJWT = jwtService.verifyToken(token, false);
            scope = signedJWT.getJWTClaimsSet().getClaim("scope").toString();
        } catch (AppException | ParseException e) {
            isValid = false;
        }
        return IntrospectResponse.builder().valid(isValid).scope(scope).build();
    }

}
