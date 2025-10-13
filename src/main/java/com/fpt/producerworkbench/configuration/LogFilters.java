package com.fpt.producerworkbench.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

@Slf4j
public final class LogFilters {
    private LogFilters() {}

    public static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(req -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[SN-REQ] ").append(req.method()).append(' ').append(req.url());
            req.headers().forEach((k, v) -> {
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(k)) {
                    sb.append("\n  ").append(k).append(": Bearer ****");
                } else {
                    sb.append("\n  ").append(k).append(": ").append(String.join(",", v));
                }
            });
            log.info(sb.toString());
            return Mono.just(req);
        });
    }

    public static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(res -> {
            log.info("[SN-RES] status={} {}", res.statusCode().value(), res.statusCode());
            return Mono.just(res);
        });
    }
}
