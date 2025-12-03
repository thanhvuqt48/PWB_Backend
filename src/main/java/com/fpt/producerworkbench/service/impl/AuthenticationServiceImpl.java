package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.common.UserStatus;
import com.fpt.producerworkbench.dto.request.*;
import com.fpt.producerworkbench.dto.response.AuthenticationResponse;
import com.fpt.producerworkbench.dto.response.ExchangeTokenResponse;
import com.fpt.producerworkbench.dto.response.IntrospectResponse;
import com.fpt.producerworkbench.entity.InvalidatedToken;
import com.fpt.producerworkbench.entity.Portfolio;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.InvalidatedTokenRepository;
import com.fpt.producerworkbench.repository.PortfolioRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.repository.http_client.OutboundIdentityClient;
import com.fpt.producerworkbench.repository.http_client.OutboundUserClient;
import com.fpt.producerworkbench.service.AuthenticationService;
import com.fpt.producerworkbench.service.JwtService;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    @NonFinal
    @Value("${outbound.identity.client-id}")
    protected String CLIENT_ID;

    @NonFinal
    @Value("${outbound.identity.client-secret}")
    protected String CLIENT_SECRET;

    @NonFinal
    @Value("${outbound.identity.redirect-uri}")
    protected String REDIRECT_URI;

    @NonFinal
    protected final String GRANT_TYPE = "authorization_code";

    UserRepository userRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;
    JwtService jwtService;
    AuthenticationManager authenticationManager;
    OutboundIdentityClient outboundIdentityClient;
    OutboundUserClient outboundUserClient;
    PortfolioRepository portfolioRepository;

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
    }

    @Transactional
    public AuthenticationResponse outboundAuthenticate(String code) {
        ExchangeTokenResponse response = outboundIdentityClient.exchangeToken(ExchangeTokenRequest.builder()
                .code(code)
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .redirectUri(REDIRECT_URI)
                .grantType(GRANT_TYPE)
                .build());
        log.info("TOKEN RESPONSE {}", response);

        var userInfo = outboundUserClient.getUserInfo("json", response.getAccessToken());
        log.info("User info {}", userInfo);


        var user = userRepository.findByEmail(userInfo.getEmail()).orElseGet(() -> userRepository.save(User.builder()
                .email(userInfo.getEmail())
                .firstName(userInfo.getGivenName())
                .lastName(userInfo.getFamilyName())
                .avatarUrl(userInfo.getPicture())
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build()));

        ensureActive(user);

        // Tạo portfolio mặc định cho user mới
        portfolioRepository.findByUserId(user.getId()).orElseGet(() -> {
            Portfolio defaultPortfolio = Portfolio.builder()
                    .user(user)
                    .isPublic(false)
                    .sections(new HashSet<>())
                    .personalProjects(new HashSet<>())
                    .socialLinks(new HashSet<>())
                    .genres(new HashSet<>())
                    .tags(new HashSet<>())
                    .build();
            return portfolioRepository.save(defaultPortfolio);
        });

        var token = jwtService.generateToken(user);

        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        log.info("Sign-in starting");
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

            var user = (User) authentication.getPrincipal();

            ensureActive(user);

            var token = jwtService.generateToken(user);

            log.info("User {} logged in successfully", user.getEmail());

            return AuthenticationResponse.builder().token(token).authenticated(true).build();
        } catch (BadCredentialsException e) {
            log.info("User {} login failed", request.getUsername());
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
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

            ensureActive(user);

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
