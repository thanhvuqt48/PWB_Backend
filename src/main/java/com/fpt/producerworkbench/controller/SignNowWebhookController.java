package com.fpt.producerworkbench.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.configuration.SignNowClient;
import com.fpt.producerworkbench.configuration.SignNowProperties;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.service.ContractSigningService;
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

@RestController
@RequestMapping("/api/v1/integrations/signnow/webhook")
@RequiredArgsConstructor
@Slf4j
public class SignNowWebhookController {

    private final SignNowProperties props;
    private final ContractRepository contractRepository;
    private final ContractSigningService contractSigningService;
    private final SignNowClient signNowClient;
    private final ObjectMapper om = new ObjectMapper();

    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handle(
            @RequestHeader(value = "X-SignNow-Signature", required = false) String signature,
            @RequestParam(required = false) Map<String, String> query,
            @RequestBody byte[] rawBody,
            HttpServletRequest req
    ) {
        String secret = props.getWebhook().getSecretKey();
        if (!verifyHmac(signature, rawBody, secret)) {
            log.warn("[Webhook] Invalid HMAC signature");
            return ResponseEntity.status(401).body("invalid signature");
        }

        final String bodyStr = new String(rawBody, StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = om.readTree(bodyStr);
        } catch (Exception ex) {
            log.warn("[Webhook] Bad payload JSON: {}", ex.getMessage());
            return ResponseEntity.badRequest().body("bad payload");
        }

        String event = firstNonEmpty(
                textAt(root, "/meta/event"),
                textAt(root, "/event")
        );
        if (!StringUtils.hasText(event)) {
            log.warn("[Webhook] Missing event. body={}", bodyStr);
            return ResponseEntity.badRequest().body("missing event");
        }

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

        if (!event.endsWith(".complete")) {
            return ResponseEntity.ok("ignored");
        }
        if (!StringUtils.hasText(documentId)) {
            log.warn("[Webhook] No documentId in payload/query.");
            return ResponseEntity.ok("no doc id");
        }

        Optional<Contract> opt = contractRepository.findBySignnowDocumentId(documentId);
        if (opt.isEmpty()) {
            log.warn("[Webhook] No contract matches signNow docId={}", documentId);
            return ResponseEntity.ok("no related contract");
        }
        Contract c = opt.get();

        if (c.getStatus() == com.fpt.producerworkbench.common.ContractStatus.COMPLETED) {
            return ResponseEntity.ok("already completed");
        }

        boolean withHistory = props.getWebhook().isWithHistory();
        try {
            if (!signNowClient.canDownloadCollapsed(documentId, withHistory)) {
                return ResponseEntity.accepted().body("collapsed not ready");
            }
        } catch (Exception e) {
            log.debug("[Webhook] canDownloadCollapsed check failed: {}", e.getMessage());
        }

        try {
            contractSigningService.saveSignedAndComplete(c.getId(), withHistory);
            return ResponseEntity.ok("ok");
        } catch (AppException ex) {
            return ResponseEntity.accepted().body("not ready");
        } catch (Exception ex) {
            log.error("[Webhook] saveSignedAndComplete failed", ex);
            return ResponseEntity.internalServerError().body("error");
        }
    }


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

    private boolean slowEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int res = 0;
        for (int i = 0; i < a.length(); i++) res |= a.charAt(i) ^ b.charAt(i);
        return res == 0;
    }

    private String textAt(JsonNode root, String jsonPtr) {
        try {
            JsonNode n = root.at(jsonPtr);
            return n.isMissingNode() ? null : n.asText(null);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String firstNonEmpty(String... vals) {
        if (vals == null) return null;
        for (String v : vals) if (StringUtils.hasText(v)) return v;
        return null;
    }

    private String getFromQuery(Map<String, String> q, String... keys) {
        if (q == null || q.isEmpty() || keys == null) return null;
        for (String k : keys) {
            String v = q.get(k);
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }

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
