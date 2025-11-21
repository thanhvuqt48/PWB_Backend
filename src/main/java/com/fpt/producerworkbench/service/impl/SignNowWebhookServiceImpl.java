package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.configuration.SignNowProperties;
import com.fpt.producerworkbench.service.SignNowWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

@Service
@Slf4j
public class SignNowWebhookServiceImpl implements SignNowWebhookService {

    private final WebClient apiClient;
    private final SignNowProperties props;
    private final SignNowClient signNowClient;

    public SignNowWebhookServiceImpl(
            @Qualifier("signNowApiWebClient") WebClient apiClient,
            SignNowProperties props,
            SignNowClient signNowClient
    ) {
        this.apiClient = apiClient;
        this.props = props;
        this.signNowClient = signNowClient;
    }

    @Override
    @Async
    public void ensureCompletedEventForDocument(String documentId) {
        if (documentId == null || documentId.isBlank()) return;

        try {
            Map<String, Object> me = apiClient.get()
                    .uri("/user")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            log.info("[Webhook] SN user: id={}, email={}", str(me == null ? null : me.get("id")),
                    str(me == null ? null : me.get("email")));
        } catch (WebClientResponseException e) {
            log.warn("[Webhook] /user failed: status={} body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        }

        List<Map<String, Object>> subs;
        try {
            subs = signNowClient.listEventSubscriptionsBearer();
        } catch (Exception e) {
            log.warn("[Webhook] list events (Bearer) exception: {}", e.toString());
            subs = Collections.emptyList();
        }
        if (subs.isEmpty()) {
            try {
                subs = signNowClient.listEventSubscriptions();
            } catch (WebClientResponseException e2) {
                log.warn("[Webhook] list events (Basic) failed: status={} body={}",
                        e2.getStatusCode().value(), e2.getResponseBodyAsString());
                subs = Collections.emptyList();
            }
        }

        for (var s : subs) {
            String event    = str(s.get("event"));
            String entityId = str(s.get("entity_id"));
            if ("document.complete".equalsIgnoreCase(event) && documentId.equals(entityId)) {
                log.info("[Webhook] document.complete already subscribed for docId={}", documentId);
                return;
            }
        }

        String cb = props.getWebhook().getCallbackUrl();
        String secret = props.getWebhook().getSecretKey();

        log.info("[Webhook] Registering webhook for documentId={}, callbackUrl={}", documentId, cb);
        
        try {
            Map<String, Object> created = signNowClient.createDocumentEventSubscriptionBearer(
                    documentId, cb, secret, true
            );
            log.info("[Webhook] Created document.complete (Bearer) for docId={}: {}", documentId, created);
        } catch (WebClientResponseException e) {
            int sc = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            log.error("[Webhook] Create document.complete (Bearer) failed for docId={}: status={} body={}",
                    documentId, sc, body);
            // Nếu lỗi 409 (Conflict) hoặc 422 (Unprocessable Entity), có thể webhook đã tồn tại
            if (sc == 409 || sc == 422) {
                log.info("[Webhook] Webhook may already exist for docId={}, will verify", documentId);
            }
        } catch (Exception e) {
            log.error("[Webhook] Create document.complete (Bearer) EX for docId={}: {}", documentId, e.toString(), e);
        }

        // Verify webhook đã được đăng ký thành công
        try {
            Thread.sleep(500); // Đợi một chút để SignNow sync
            Optional<String> subscriptionId = signNowClient.findDocumentCompleteSubscriptionIdBasic(documentId);
            if (subscriptionId.isPresent()) {
                log.info("[Webhook] Verified webhook subscription for docId={}, subscriptionId={}", 
                        documentId, subscriptionId.get());
            } else {
                log.warn("[Webhook] Webhook subscription NOT FOUND for docId={} after registration. " +
                        "This may cause webhook events to be missed!", documentId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Webhook] Interrupted while verifying webhook for docId={}", documentId);
        } catch (WebClientResponseException e) {
            log.warn("[Webhook] Verify subscription (Basic) failed for docId={}: status={} body={}",
                    documentId, e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("[Webhook] Verify subscription failed for docId={}: {}", documentId, e.toString());
        }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}





