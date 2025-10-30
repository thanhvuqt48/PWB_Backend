package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.service.SignNowAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(SignNowProperties.class)
@RequiredArgsConstructor
@Slf4j
public class SignNowConfig {

    private final SignNowProperties props;

    private String resolvedBaseUrl() {
        String base = props.getBaseUrl();
        if (base == null || base.isBlank() || base.contains("{")) {
            throw new IllegalStateException("signnow.base-url chưa resolve. Hãy dùng ${SN_BASE_URL} trong YAML và set env.");
        }
        return base;
    }

    private HttpClient httpClient() {
        TcpClient tcp = TcpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getHttp().getConnectTimeoutMs())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new io.netty.handler.timeout.ReadTimeoutHandler(Math.max(1, props.getHttp().getReadTimeoutMs() / 1000))
                ));
        return HttpClient.from(tcp);
    }

    private ExchangeFilterFunction bearerInjector(SignNowAuthService authSvc) {
        return (request, next) -> {
            String path = request.url().getPath();
            boolean isToken = path.startsWith("/oauth2/token");
            boolean hasAuth = request.headers().containsKey(HttpHeaders.AUTHORIZATION);
            if (isToken || hasAuth) return next.exchange(request);

            String token = authSvc.getAccessToken();
            String first8 = token == null ? "null" : token.substring(0, Math.min(8, token.length()));
            String last6  = token == null ? "null" : token.substring(Math.max(0, token.length() - 6));
            ClientRequest withBearer = ClientRequest.from(request)
                    .headers(h -> h.setBearerAuth(token))
                    .build();

            log.info("[SN][BearerInject] {} {}  auth=Bearer {}...{}", request.method(), request.url(), first8, last6);
            return next.exchange(withBearer);
        };
    }

    private ExchangeFilterFunction retryOn401(SignNowAuthService authSvc) {
        return (request, next) -> next.exchange(request)
                .flatMap(res -> {
                    if (res.statusCode().equals(HttpStatus.UNAUTHORIZED)) {
                        String newToken = authSvc.forceRefresh();
                        ClientRequest retry = ClientRequest.from(request)
                                .headers(h -> {
                                    if (!request.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                                        h.setBearerAuth(newToken);
                                    }
                                })
                                .build();
                        log.warn("[SN][Retry401] Retry with fresh token for {} {}", request.method(), request.url());
                        return next.exchange(retry);
                    }
                    return Mono.just(res);
                });
    }

    @Bean("signNowAuthWebClient")
    public WebClient signNowAuthWebClient() {
        return WebClient.builder()
                .baseUrl(resolvedBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .build();
    }


    @Bean("signNowApiWebClient")
    public WebClient signNowApiWebClient(SignNowAuthService authSvc) {
        return WebClient.builder()
                .baseUrl(resolvedBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .filter(bearerInjector(authSvc))
                .filter(retryOn401(authSvc))
                .filter((request, next) -> {
                    log.info("[SN][REQ] {} {}", request.method(), request.url());
                    return next.exchange(request)
                            .doOnNext(resp -> log.info("[SN][RES] {} {} -> {}", request.method(), request.url(), resp.statusCode().value()));
                })
                .build();
    }

    @Bean("signNowEventsWebClient")
    public WebClient signNowEventsWebClient() {
        return WebClient.builder()
                .baseUrl(resolvedBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .defaultHeaders(h -> {
                    h.setBasicAuth(props.getClientId(), props.getClientSecret(), StandardCharsets.UTF_8);
                    h.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                })
                .filter((req, next) -> {
                    log.info("[SN][Events][REQ] {} {}", req.method(), req.url());
                    return next.exchange(req).doOnNext(res ->
                            log.info("[SN][Events][RES] {} -> {}", req.url(), res.statusCode().value())
                    );
                })
                .build();
    }

}

