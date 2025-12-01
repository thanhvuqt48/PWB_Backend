package com.fpt.producerworkbench.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.configuration.SignNowProperties;
import com.fpt.producerworkbench.repository.ContractAddendumRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.SignNowWebhookEventService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Controller xử lý webhook từ SignNow
 * Chỉ làm nhiệm vụ verify HMAC, parse JSON và điều hướng đến service
 */
@RestController
@RequestMapping("/api/v1/integrations/signnow/webhook")
@RequiredArgsConstructor
@Slf4j
public class SignNowWebhookController {

    private final SignNowProperties props;
    private final ContractRepository contractRepository;
    private final ContractAddendumRepository contractAddendumRepository;
    private final SignNowWebhookEventService webhookEventService;
    private final ObjectMapper om = new ObjectMapper();

    /**
     * Main webhook handler - chỉ làm nhiệm vụ verify HMAC, parse JSON và điều hướng
     */
    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handle(
            @RequestHeader(value = "X-SignNow-Signature", required = false) String signature,
            @RequestParam(required = false) Map<String, String> query,
            @RequestBody byte[] rawBody,
            HttpServletRequest req
    ) {
        // Verify HMAC signature
        String secret = props.getWebhook().getSecretKey();
        if (!verifyHmac(signature, rawBody, secret)) {
            log.warn("[Webhook] Invalid HMAC signature");
            return ResponseEntity.status(401).body("invalid signature");
        }

        // Parse JSON payload
        final String bodyStr = new String(rawBody, StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = om.readTree(bodyStr);
        } catch (Exception ex) {
            log.warn("[Webhook] Bad payload JSON: {}", ex.getMessage());
            return ResponseEntity.badRequest().body("bad payload");
        }

        // Extract event type
        String event = firstNonEmpty(
                textAt(root, "/meta/event"),
                textAt(root, "/event")
        );
        if (!StringUtils.hasText(event)) {
            log.warn("[Webhook] Missing event. body={}", bodyStr);
            return ResponseEntity.badRequest().body("missing event");
        }

        // Extract document ID
        String documentId = firstNonEmpty(
                getFromQuery(query, "docid", "docId", "document_id", "documentId", "entity_id", "entityId", "document"),
                extractFromParamJson(query, "Param", "docId", "documentId", "document_id"),
                textAt(root, "/content/documentId"),
                textAt(root, "/content/document_id"),
                textAt(root, "/documentId"),
                textAt(root, "/document_id"),
                textAt(root, "/entityId"),
                textAt(root, "/entity_id")
        );

        log.info("[Webhook] event={} docId={}", event, documentId);

        // Filter events: chỉ xử lý các event liên quan đến ký
        // - document.fieldinvite.complete: Một người đã ký xong
        // - document.complete: Tất cả người đã ký xong (source of truth)
        // - document.signed, signer.signed: Các event signed khác
        // Mở rộng điều kiện check để bắt được các biến thể của tên sự kiện
        boolean isFieldInviteComplete = "document.fieldinvite.complete".equals(event) 
                                     || "fieldinvite.complete".equals(event);
        boolean isDocumentComplete = "document.complete".equals(event);
        boolean isCompleteEvent = isFieldInviteComplete || isDocumentComplete;
        boolean isSignedEvent = event.endsWith(".signed") || event.contains(".signed.");

        if (!isCompleteEvent && !isSignedEvent) {
            return ResponseEntity.ok("ignored");
        }
        if (!StringUtils.hasText(documentId)) {
            log.warn("[Webhook] No documentId in payload/query.");
            return ResponseEntity.ok("no doc id");
        }

        // Route to appropriate handler via service
        Optional<com.fpt.producerworkbench.entity.Contract> optContract = contractRepository.findBySignnowDocumentId(documentId);
        if (optContract.isPresent()) {
            return webhookEventService.handleContractEvent(
                    optContract.get(), 
                    documentId, 
                    event, 
                    isFieldInviteComplete, 
                    isDocumentComplete, 
                    isSignedEvent
            );
        }

        Optional<com.fpt.producerworkbench.entity.ContractAddendum> optAddendum = contractAddendumRepository.findBySignnowDocumentId(documentId);
        if (optAddendum.isPresent()) {
            return webhookEventService.handleAddendumEvent(
                    optAddendum.get(), 
                    documentId, 
                    event, 
                    isFieldInviteComplete, 
                    isDocumentComplete, 
                    isSignedEvent
            );
        }

        log.warn("[Webhook] No contract/addendum matches signNow docId={}", documentId);
        return ResponseEntity.ok("no related contract/addendum");
    }

    // ============================================================================
    // Helper Functions - Chỉ dùng cho parsing và verification
    // ============================================================================

    /**
     * Verify HMAC signature từ SignNow
     */
    private boolean verifyHmac(String signature, byte[] body, String secret) {
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(secret)) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(body);
            String expected = Base64.getEncoder().encodeToString(raw);
            return slowEquals(signature, expected);
        } catch (Exception e) {
            log.warn("[Webhook] HMAC error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Constant-time string comparison để tránh timing attack
     */
    private boolean slowEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int res = 0;
        for (int i = 0; i < a.length(); i++) res |= a.charAt(i) ^ b.charAt(i);
        return res == 0;
    }
    
    /**
     * Extract text value từ JSON node bằng JSON pointer
     */
    private String textAt(JsonNode root, String jsonPtr) {
        try {
            JsonNode n = root.at(jsonPtr);
            return n.isMissingNode() ? null : n.asText(null);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * Tìm giá trị đầu tiên không rỗng trong danh sách
     */
    private String firstNonEmpty(String... vals) {
        if (vals == null) return null;
        for (String v : vals) if (StringUtils.hasText(v)) return v;
        return null;
    }

    /**
     * Lấy giá trị từ query parameters với nhiều key options
     */
    private String getFromQuery(Map<String, String> q, String... keys) {
        if (q == null || q.isEmpty() || keys == null) return null;
        for (String k : keys) {
            String v = q.get(k);
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }

    /**
     * Extract document ID từ JSON trong query parameter
     */
    private String extractFromParamJson(Map<String, String> q, String paramKey, String... docKeys) {
        if (q == null) return null;
        String raw = firstNonEmpty(q.get(paramKey), q.get(paramKey != null ? paramKey.toLowerCase() : null));
        if (!StringUtils.hasText(raw)) return null;
        try {
            String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            JsonNode n = om.readTree(decoded);
            for (String dk : docKeys) {
                String v = n.path(dk).asText(null);
                if (StringUtils.hasText(v)) return v;
            }
        } catch (Exception ignore) { }
        return null;
    }
}
