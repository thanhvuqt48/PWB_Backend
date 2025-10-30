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

        log.info("[SN][AUTH][INIT] baseUrl={} user={} clientId={} secret=****",
                safe(props.getBaseUrl()),
                safe(props.getAuth() == null ? null : props.getAuth().getUsername()),
                headTail(props.getClientId()));
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

    private static String headTail(String s) {
        if (s == null) return "null";
        if (s.length() <= 12) return s;
        return s.substring(0, 8) + "..."+ s.substring(s.length()-6);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "<empty>" : s;
    }

    private static long secondsLeft(Instant expiresAt) {
        if (expiresAt == null) return -1;
        return Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
    }

    private void logSnapshot(String tag, Tok t) {
        if (t == null) {
            log.info("[SN][AUTH] {} cache=NULL", tag);
            return;
        }
        log.info("[SN][AUTH] {} access={} refresh={} ttl={}s",
                tag,
                headTail(t.accessToken),
                (t.refreshToken == null ? "<none>" : headTail(t.refreshToken)),
                secondsLeft(t.expiresAt));
    }

    @Override
    public String getAccessToken() {
        Tok t = cache.get();
        if (t != null) {
            long ttl = secondsLeft(t.expiresAt);
            logSnapshot("GET (before)", t);
            if (ttl > 30) {
                log.info("[SN][AUTH] GET using CACHED access token (ttl={}s)", ttl);
                return t.accessToken;
            }
            log.info("[SN][AUTH] GET token is near-expired (ttl={}s) -> refresh by password grant", ttl);
        } else {
            log.info("[SN][AUTH] GET no cache -> obtain by password grant");
        }
        return obtainWithPasswordGrant();
    }

    @Override
    public String forceRefresh() {
        Tok t = cache.get();
        if (t == null || t.refreshToken == null) {
            log.info("[SN][AUTH] forceRefresh: no refresh token in cache -> password grant");
            return obtainWithPasswordGrant();
        }
        log.info("[SN][AUTH] forceRefresh: using refresh={}...", headTail(t.refreshToken));
        return obtainWithRefreshToken(t.refreshToken);
    }

    private String obtainWithPasswordGrant() {
        String username = props.getAuth().getUsername();
        log.info("[SN][AUTH] PASSWORD GRANT start baseUrl={} user={} clientId={}",
                safe(props.getBaseUrl()), safe(username), headTail(props.getClientId()));

        Map<String, Object> body;
        try {
            body = authClient.post()
                    .uri("/oauth2/token")
                    .headers(h -> {
                        h.setBasicAuth(props.getClientId(), props.getClientSecret(), StandardCharsets.UTF_8);
                        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                    })
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "password")
                            .with("username", username)
                            .with("password", props.getAuth().getPassword()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class).defaultIfEmpty("<empty>").flatMap(b -> {
                                int code = r.statusCode().value();
                                log.error("[SN][AUTH] PASSWORD GRANT HTTP {} body={}", code, b);
                                return Mono.error(new WebClientResponseException(
                                        "Token request (password) failed", code, r.statusCode().toString(),
                                        null, b.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
                            }))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (WebClientResponseException e) {
            log.error("[SN][AUTH] PASSWORD GRANT EX: status={} body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("[SN][AUTH] PASSWORD GRANT EX: {}", e.toString());
            throw e;
        }

        String at = cacheAndReturn(body);
        Tok t = cache.get();
        log.info("[SN][AUTH] PASSWORD GRANT ok access={} refresh={} ttl={}s",
                headTail(at),
                (t.refreshToken == null ? "<none>" : headTail(t.refreshToken)),
                secondsLeft(t.expiresAt));
        return at;
    }

    private String obtainWithRefreshToken(String refreshToken) {
        log.info("[SN][AUTH] REFRESH GRANT start baseUrl={} refresh={}",
                safe(props.getBaseUrl()), headTail(refreshToken));
        Map<String, Object> body;
        try {
            body = authClient.post()
                    .uri("/oauth2/token")
                    .headers(h -> h.setBasicAuth(props.getClientId(), props.getClientSecret(), StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                            .with("refresh_token", refreshToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class).defaultIfEmpty("<empty>").flatMap(b -> {
                                int code = r.statusCode().value();
                                log.error("[SN][AUTH] REFRESH GRANT HTTP {} body={}", code, b);
                                return Mono.error(new WebClientResponseException(
                                        "Token request (refresh) failed", code, r.statusCode().toString(),
                                        null, b.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
                            }))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (WebClientResponseException e) {
            log.error("[SN][AUTH] REFRESH GRANT EX: status={} body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("[SN][AUTH] REFRESH GRANT EX: {}", e.toString());
            throw e;
        }

        String at = cacheAndReturn(body);
        Tok t = cache.get();
        log.info("[SN][AUTH] REFRESH GRANT ok access={} refresh={} ttl={}s",
                headTail(at),
                (t.refreshToken == null ? "<none>" : headTail(t.refreshToken)),
                secondsLeft(t.expiresAt));
        return at;
    }

    private String cacheAndReturn(Map<String, Object> m) {
        if (m == null) throw new IllegalStateException("Empty token response");
        String at = (String) m.get("access_token");
        String rt = (String) m.get("refresh_token");
        Number expiresIn = (Number) m.get("expires_in");
        String tokenType = (String) m.get("token_type");
        String scope = (String) m.get("scope");

        if (at == null) throw new IllegalStateException("No access_token in response");

        Instant ea = (expiresIn == null)
                ? Instant.now().plusSeconds(3600)
                : Instant.now().plusSeconds(expiresIn.longValue());

        cache.set(new Tok(at, rt, ea));

        log.info("[SN][AUTH] token_type={} scope={} expires_in={}s",
                safe(tokenType), safe(scope), (expiresIn == null ? "null" : String.valueOf(expiresIn)));
        return at;
    }
}

