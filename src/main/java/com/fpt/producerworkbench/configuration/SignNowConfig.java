package com.fpt.producerworkbench.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector; // <-- đúng
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

@Configuration
@EnableConfigurationProperties(SignNowProperties.class) // <-- ĐĂNG KÝ bean properties ở đây
@RequiredArgsConstructor
@Slf4j
public class SignNowConfig {

    private final SignNowProperties props;

    private String resolvedBaseUrl() {
        String base = props.getBaseUrl();
        if (base == null || base.isBlank() || base.contains("{")) {
            throw new IllegalStateException("signnow.base-url chưa resolve. Dùng ${SN_BASE_URL} trong YAML và set env.");
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

    // Filter gắn Bearer (trừ khi request đã có Authorization hoặc đang gọi /oauth2/token)
    private ExchangeFilterFunction bearerInjector(SignNowAuthService authSvc) {
        return (request, next) -> {
            String path = request.url().getPath();
            boolean isToken = path.startsWith("/oauth2/token");
            boolean hasAuth = request.headers().containsKey(HttpHeaders.AUTHORIZATION);
            if (isToken || hasAuth) return next.exchange(request);
            String token = authSvc.getAccessToken();
            ClientRequest withBearer = ClientRequest.from(request)
                    .headers(h -> h.setBearerAuth(token))
                    .build();
            return next.exchange(withBearer);
        };
    }

    // Nếu 401 thì refresh và retry 1 lần
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
                        return next.exchange(retry);
                    }
                    return Mono.just(res);
                });
    }

    /** WebClient dành cho OAuth: KHÔNG có bearer filter */
    @Bean("signNowAuthWebClient")
    public WebClient signNowAuthWebClient() {
        return WebClient.builder()
                .baseUrl(resolvedBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient())) // <-- đúng class
                .filter(LogFilters.logRequest())
                .filter(LogFilters.logResponse())
                .build();
    }

    /** WebClient API: có Bearer + auto-refresh */
    @Bean("signNowApiWebClient")
    public WebClient signNowApiWebClient(SignNowAuthService authSvc) {
        return WebClient.builder()
                .baseUrl(resolvedBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient())) // <-- đúng class
                .filter(LogFilters.logRequest())
                .filter(LogFilters.logResponse())
                .filter(bearerInjector(authSvc))
                .filter(retryOn401(authSvc))
                .build();
    }
}
