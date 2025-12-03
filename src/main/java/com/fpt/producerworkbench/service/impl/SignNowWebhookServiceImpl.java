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

        // 1. Get User Info (Giữ nguyên để check connection)
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
        } catch (Exception ignored) {
            // Ignore other exceptions
        }

        // 2. Lấy danh sách webhook đang tồn tại
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
            } catch (Exception e2) {
                subs = Collections.emptyList();
            }
        }

        // 3. Danh sách các event CẦN PHẢI CÓ
        List<String> requiredEvents = Arrays.asList(
                "document.complete",            // Khi tất cả đã ký
                "document.fieldinvite.signed"   // Khi có người ký (để update PARTIALLY_SIGNED)
        );

        String cb = props.getWebhook().getCallbackUrl();
        String secret = props.getWebhook().getSecretKey();

        // 4. Duyệt qua từng event cần thiết và đăng ký nếu chưa có
        for (String targetEvent : requiredEvents) {
            boolean exists = false;
            for (var s : subs) {
                String existingEvent = str(s.get("event"));
                String existingEntityId = str(s.get("entity_id"));
                
                // So sánh chính xác event và documentId
                if (targetEvent.equalsIgnoreCase(existingEvent) && documentId.equals(existingEntityId)) {
                    log.info("[Webhook] Event '{}' already subscribed for docId={}", targetEvent, documentId);
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                log.info("[Webhook] Registering event '{}' for documentId={}", targetEvent, documentId);
                try {
                    // Gọi hàm create đã sửa, truyền targetEvent vào
                    Map<String, Object> created = signNowClient.createDocumentEventSubscriptionBearer(
                            documentId, cb, secret, true, targetEvent
                    );
                    log.info("[Webhook] Successfully created event '{}' for docId={}: {}", targetEvent, documentId, created);
                } catch (WebClientResponseException e) {
                    int sc = e.getStatusCode().value();
                    if (sc == 409 || sc == 422) {
                        log.info("[Webhook] Event '{}' may already exist (Conflict/Unprocessable) for docId={}", 
                                targetEvent, documentId);
                    } else {
                        log.error("[Webhook] Failed to create event '{}' for docId={}: status={} body={}", 
                                targetEvent, documentId, sc, e.getResponseBodyAsString());
                    }
                } catch (Exception e) {
                    log.error("[Webhook] Exception creating event '{}' for docId={}: {}", 
                            targetEvent, documentId, e.getMessage(), e);
                }
            }
        }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}





