package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.configuration.SignNowProperties;
import com.fpt.producerworkbench.service.SignNowAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class SignNowAuthServiceImpl implements SignNowAuthService {

    private final WebClient authClient;
    private final SignNowProperties props;

    public SignNowAuthServiceImpl(
            @Qualifier("signNowAuthWebClient") WebClient authClient,
            SignNowProperties props
    ) {
        this.authClient = authClient;
        this.props = props;
    }

    @Override
    public String getFromEmail() {
        return props.getAuth().getUsername();
    }

    private static final class Tok {
        final String accessToken, refreshToken; final Instant expiresAt;
        Tok(String at, String rt, Instant ea) { accessToken = at; refreshToken = rt; expiresAt = ea; }
    }

    private final AtomicReference<Tok> cache = new AtomicReference<>();

    @Override
    public String getAccessToken() {
        Tok t = cache.get();
        if (t != null && t.expiresAt != null && t.expiresAt.isAfter(Instant.now().plusSeconds(30))) return t.accessToken;
        return obtainWithPasswordGrant();
    }

    @Override
    public String forceRefresh() {
        Tok t = cache.get();
        if (t == null || t.refreshToken == null) return obtainWithPasswordGrant();
        return obtainWithRefreshToken(t.refreshToken);
    }

    private String obtainWithPasswordGrant() {
        Map<String, Object> body = authClient.post()
                .uri("/oauth2/token")
                .headers(h -> {
                    h.setBasicAuth(props.getClientId(), props.getClientSecret(), StandardCharsets.UTF_8);
                    h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "password")
                        .with("username", props.getAuth().getUsername())
                        .with("password", props.getAuth().getPassword()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).defaultIfEmpty("<empty>").flatMap(b -> {
                            int code = r.statusCode().value();
                            return Mono.error(new WebClientResponseException(
                                    "Token request failed", code, r.statusCode().toString(),
                                    null, b.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
                        }))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
        return cacheAndReturn(body);
    }

    private String obtainWithRefreshToken(String refreshToken) {
        Map<String, Object> body = authClient.post()
                .uri("/oauth2/token")
                .headers(h -> h.setBasicAuth(props.getClientId(), props.getClientSecret(), StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "refresh_token").with("refresh_token", refreshToken))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
        return cacheAndReturn(body);
    }

    private String cacheAndReturn(Map<String, Object> m) {
        if (m == null) throw new IllegalStateException("Empty token response");
        String at = (String) m.get("access_token");
        String rt = (String) m.get("refresh_token");
        Number expiresIn = (Number) m.get("expires_in");
        if (at == null) throw new IllegalStateException("No access_token");
        Instant ea = expiresIn == null ? Instant.now().plusSeconds(3600) : Instant.now().plusSeconds(expiresIn.longValue());
        cache.set(new Tok(at, rt, ea));
        return at;
    }
}
