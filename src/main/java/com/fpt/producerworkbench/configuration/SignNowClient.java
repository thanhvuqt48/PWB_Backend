package com.fpt.producerworkbench.configuration;

import com.fpt.producerworkbench.service.SignNowAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
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

    private final WebClient apiClient;
    private final SignNowAuthService authService;

    public SignNowClient(
            @Qualifier("signNowApiWebClient") WebClient apiClient,
            SignNowAuthService authService
    ) {
        this.apiClient = apiClient;
        this.authService = authService;
    }

    private static String cut(String s) {
        if (s == null) return "null";
        int max = 2000;
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    private Mono<? extends Throwable> toError(String tag, int raw, HttpStatusCode code, String body) {
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
}

