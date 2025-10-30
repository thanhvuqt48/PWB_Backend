package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.service.SignNowAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SignNowClient {

    private final SignNowAuthService authService;
    private final SignNowProperties props;
    @Qualifier("signNowApiWebClient")
    private final WebClient apiClient;
    @Qualifier("signNowEventsWebClient")
    private final WebClient eventsClient;

    public SignNowClient(
            @Qualifier("signNowApiWebClient") WebClient apiClient,
            SignNowAuthService authService,
            SignNowProperties props,
            @Qualifier("signNowEventsWebClient") WebClient eventsClient
    ) {
        this.apiClient = apiClient;
        this.authService = authService;
        this.props = props;
        this.eventsClient = eventsClient;
    }

    private static String cut(String s) {
        if (s == null) return "null";
        int max = 2000;
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    private <T> Mono<T> toError(String tag, int raw, HttpStatusCode code, String body) {
        String safe = cut(body);
        return Mono.error(new WebClientResponseException(
                "SignNow " + tag + " failed: " + code,
                raw,
                code.toString(),
                null,
                safe.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));
    }

    private String defaultFrom() {
        String from = authService.getFromEmail();
        if (from == null || from.isBlank()) throw new IllegalStateException("signnow.auth.username is empty");
        return from;
    }


    public String uploadDocumentWithFieldExtract(byte[] pdf, String filename) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(pdf) {
            @Override public String getFilename() { return filename; }
        }).filename(filename);

        Map<String, Object> resp = apiClient.post()
                .uri("/document/fieldextract")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                .flatMap(b -> toError("fieldextract(upload)", r.statusCode().value(), r.statusCode(), b)))
                .bodyToMono(Map.class)
                .block();

        return resp == null ? null : (String) resp.get("id");
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getRoleIdMap(String docId) {
        Map<String, Object> doc = apiClient.get()
                .uri("/document/{id}", docId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                .flatMap(b -> toError("getDocument", r.statusCode().value(), r.statusCode(), b)))
                .bodyToMono(Map.class)
                .block();

        Map<String, String> map = new LinkedHashMap<>();
        Object rolesObj = doc == null ? null : doc.get("roles");
        if (rolesObj instanceof List<?> roles) {
            for (Object r : roles) {
                Map<String, Object> mm = (Map<String, Object>) r;
                String name = (String) (mm.get("name") != null ? mm.get("name") : mm.get("role"));
                String id   = (String) (mm.get("unique_id") != null ? mm.get("unique_id") : mm.get("id"));
                if (name != null && id != null) map.put(name, id);
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public String getDocumentOwnerEmail(String docId) {
        Map<String, Object> doc = apiClient.get()
                .uri("/document/{id}", docId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                .flatMap(b -> toError("getDocument(owner)", r.statusCode().value(), r.statusCode(), b)))
                .bodyToMono(Map.class)
                .block();

        if (doc == null) return null;
        Object owner = doc.get("owner");
        if (owner instanceof String s) return s;
        if (owner instanceof Map<?,?> m) {
            Object email = m.get("email");
            return email == null ? null : String.valueOf(email);
        }
        return authService.getFromEmail();
    }

    public Map<String, Object> createFieldInvite(String docId, List<Map<String, Object>> to, boolean sequential, String fromEmail) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("signing_order", sequential ? "sequential" : "parallel");
        payload.put("from", (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : defaultFrom());
        payload.put("to", to);

        return apiClient.post()
                .uri("/document/{id}/invite", docId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                .flatMap(b -> toError("createFieldInvite", r.statusCode().value(), r.statusCode(), b)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public Map<String, Object> createFreeFormInvite(String documentId, List<String> emails, boolean sequential, String fromEmail) {
        Map<String, Object> body = new HashMap<>();
        body.put("from", (fromEmail == null || fromEmail.isBlank()) ? defaultFrom() : fromEmail);
        body.put("signing_order", sequential ? "sequential" : "parallel");
        body.put("freeform_invite", true);
        List<Map<String, Object>> to = emails.stream().map(e -> {
            Map<String, Object> m = new HashMap<>(); m.put("email", e); return m;
        }).collect(Collectors.toList());
        body.put("to", to);

        try {
            return apiClient.post()
                    .uri("/v2/documents/{id}/email-invites", documentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                    .flatMap(b -> toError("createFreeFormInviteV2", r.statusCode().value(), r.statusCode(), b)))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 405) {
                return apiClient.post()
                        .uri("/v2/documents/{id}/free-form-invites", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, r ->
                                r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                        .flatMap(b -> toError("createFreeFormInviteV2Fallback", r.statusCode().value(), r.statusCode(), b)))
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .block();
            }
            throw ex;
        }
    }

    public boolean canDownloadCollapsed(String documentId, boolean withHistory) {
        try {
            apiClient.get()
                    .uri(b -> {
                        var u = b.path("/document/{id}/download")
                                .queryParam("type", "collapsed");
                        if (withHistory) u = u.queryParam("with_history", 1);
                        return u.build(documentId);
                    })
                    .accept(MediaType.APPLICATION_PDF)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (WebClientResponseException ex) {
            int sc = ex.getStatusCode().value();
            if (sc == 400 || sc == 409 || sc == 422) return false;
            throw ex;
        }
    }

    public byte[] downloadFinalPdf(String documentId, boolean withHistory) {
        return apiClient.get()
                .uri(b -> {
                    var u = b.path("/document/{id}/download").queryParam("type", "collapsed");
                    if (withHistory) u = u.queryParam("with_history", 1);
                    return u.build(documentId);
                })
                .accept(MediaType.APPLICATION_PDF)
                .exchangeToMono(resp -> {
                    if (resp.statusCode().is2xxSuccessful()) {
                        return resp.bodyToFlux(DataBuffer.class)
                                .map(db -> {
                                    byte[] bytes = new byte[db.readableByteCount()];
                                    db.read(bytes);
                                    DataBufferUtils.release(db);
                                    return bytes;
                                })
                                .collectList()
                                .map(parts -> {
                                    int total = parts.stream().mapToInt(b -> b.length).sum();
                                    byte[] all = new byte[total];
                                    int pos = 0;
                                    for (byte[] p : parts) { System.arraycopy(p, 0, all, pos, p.length); pos += p.length; }
                                    if (all.length == 0) throw new IllegalStateException("SignNow returned 200 with empty body");
                                    return all;
                                });
                    }
                    return resp.bodyToMono(String.class).defaultIfEmpty("<empty>").flatMap(body -> {
                        var sc = resp.statusCode();
                        return Mono.error(new WebClientResponseException(
                                "SignNow download failed",
                                sc.value(), sc.toString(), null,
                                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
                        ));
                    });
                })
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listEventSubscriptions() {
        return eventsClient.get()
                .uri("/api/v2/events")
                .accept(MediaType.ALL)
                .exchangeToMono(resp -> {
                    if (!resp.statusCode().is2xxSuccessful()) {
                        return resp.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                .flatMap(b -> toError("listEvents", resp.statusCode().value(), resp.statusCode(), b));
                    }
                    return resp.bodyToMono(Object.class).map(obj -> {
                        if (obj instanceof List<?> lst) return (List<Map<String, Object>>) (List<?>) lst;
                        if (obj instanceof Map<?, ?> map && map.get("data") instanceof List<?> lst2)
                            return (List<Map<String, Object>>) (List<?>) lst2;
                        log.warn("[Webhook] Unknown list-events response shape: {}", obj);
                        return Collections.<Map<String, Object>>emptyList();
                    });
                })
                .block();
    }


    public Map<String, Object> createDocumentEventSubscriptionBearer(
            String documentId, String callbackUrl, String secretKey, boolean docIdQueryParam
    ) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("callback", callbackUrl);
        if (docIdQueryParam) attrs.put("docid_queryparam", true);
        if (secretKey != null && !secretKey.isBlank()) attrs.put("secret_key", secretKey);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "document.complete");
        payload.put("entity_id", documentId);   // BẮT BUỘC: document_id
        payload.put("action", "callback");
        payload.put("attributes", attrs);

        return apiClient.post()
                .uri("/api/v2/events")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.ALL)
                .bodyValue(payload)
                .exchangeToMono(resp -> {
                    if (resp.statusCode().is2xxSuccessful()) {
                        var ct = resp.headers().contentType().orElse(MediaType.APPLICATION_JSON);
                        if (ct.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                            return resp.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                                    .defaultIfEmpty(new LinkedHashMap<>());
                        }
                        return resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(s -> {
                                    Map<String, Object> ok = new LinkedHashMap<>();
                                    ok.put("status", resp.statusCode().value()); // thường 201
                                    ok.put("body", s); // thường rỗng
                                    return ok;
                                });
                    }
                    return resp.bodyToMono(String.class).defaultIfEmpty("<empty>")
                            .flatMap(b -> toError("createDocumentEvent(Bearer)", resp.statusCode().value(), resp.statusCode(), b));
                })
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listEventSubscriptionsBearer() {
        return apiClient.get()
                .uri("/api/v2/events")
                .accept(MediaType.ALL)
                .exchangeToMono(resp -> {
                    if (!resp.statusCode().is2xxSuccessful()) {
                        return resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(body -> {
                                    log.warn("[Webhook] listEvents(Bearer) non-2xx: {} body={}",
                                            resp.statusCode().value(), cut(body));
                                    return Collections.<Map<String, Object>>emptyList();
                                });
                    }
                    return resp.bodyToMono(Object.class).map(obj -> {
                        if (obj instanceof List<?> lst) return (List<Map<String, Object>>) (List<?>) lst;
                        if (obj instanceof Map<?, ?> map && map.get("data") instanceof List<?> lst2)
                            return (List<Map<String, Object>>) (List<?>) lst2;
                        log.warn("[Webhook] Unknown list-events(Bearer) shape: {}", obj);
                        return Collections.<Map<String, Object>>emptyList();
                    });
                })
                .block();
    }

    public Optional<String> findDocumentCompleteSubscriptionIdBasic(String documentId) {
        List<Map<String, Object>> subs = listEventSubscriptions();
        for (var s : subs) {
            String ev  = String.valueOf(s.get("event"));
            String eid = String.valueOf(s.get("entity_id"));
            if ("document.complete".equalsIgnoreCase(ev) && documentId.equals(eid)) {
                Object id = s.get("id");
                return Optional.ofNullable(id == null ? null : String.valueOf(id));
            }
        }
        return Optional.empty();
    }

    public Map<String, Object> getCurrentUser() {
        return apiClient.get()
                .uri("/user")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }
}


