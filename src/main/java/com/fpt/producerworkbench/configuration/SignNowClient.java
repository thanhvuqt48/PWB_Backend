package com.fpt.producerworkbench.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class SignNowClient {

    private final WebClient apiClient;

    public SignNowClient(@Qualifier("signNowApiWebClient") WebClient apiClient) {
        this.apiClient = apiClient;
    }

    private static String cut(String s) {
        if (s == null) return "null";
        int max = 2000;
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    private <T> Mono<? extends Throwable> toError(String tag, int raw, HttpStatusCode code, String body) {
        String safe = cut(body);
        log.error("[SignNow:{}] HTTP {} {} body={}", tag, raw, code, safe);
        return Mono.error(new WebClientResponseException(
                "SignNow " + tag + " failed: " + code,
                raw,
                code.toString(),
                null,
                safe.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        ));
    }

    public String uploadDocument(byte[] pdf, String filename) {
        ByteArrayResource filePart = new ByteArrayResource(pdf) {
            @Override public String getFilename() { return filename; }
            @Override public long contentLength() { return pdf.length; }
        };

        Map<String, Object> resp = apiClient.post()
                .uri("/document")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData("file", filePart))
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                .flatMap(b -> toError("uploadDocument", r.statusCode().value(), r.statusCode(), b)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        String id = resp == null ? null : (String) resp.get("id");
        log.info("[SignNow] Uploaded document id={}", id);
        return id;
    }

    private Map<String, Object> createInvite(String documentId,
                                             List<Map<String, Object>> to,
                                             boolean sequential,
                                             String fromEmail) {
        Map<String, Object> body = new HashMap<>();
        if (fromEmail != null && !fromEmail.isBlank()) body.put("from", fromEmail);
        body.put("to", to);
        body.put("signing_order", sequential ? "sequential" : "parallel");

        return apiClient.post()
                .uri("/document/{id}/invite", documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                .flatMap(b -> toError("createInvite", r.statusCode().value(), r.statusCode(), b)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public Map<String, Object> createFieldInvite(String documentId, List<Map<String, Object>> to,
                                                 boolean sequential, String fromEmail) {
        return createInvite(documentId, to, sequential, fromEmail);
    }

    public Map<String, Object> createFreeFormInvite(String documentId, List<Map<String, Object>> to, String fromEmail) {
        return createInvite(documentId, to, false, fromEmail);
    }

    public String createEmbeddedInviteAndLink(String documentId, List<Map<String, Object>> signers, boolean sequential) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("signers", signers);
        payload.put("signing_order", sequential ? "sequential" : "parallel");

        Map<String, Object> invite = apiClient.post()
                .uri("/v2/documents/{id}/embedded-invites", documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                .flatMap(b -> toError("createEmbeddedInvite", r.statusCode().value(), r.statusCode(), b)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        String embeddedInviteId = invite == null ? null : (String) invite.get("id");
        if (embeddedInviteId == null) throw new IllegalStateException("No embedded invite id");

        Map<String, Object> link = apiClient.post()
                .uri("/v2/embedded-invites/{id}/link", embeddedInviteId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                .flatMap(b -> toError("createEmbeddedLink", r.statusCode().value(), r.statusCode(), b)))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        String url = link == null ? null : (String) link.get("url");
        if (url == null) throw new IllegalStateException("No embedded link url");
        return url;
    }

    public byte[] downloadSignedPdf(String documentId) {
        return apiClient.get()
                .uri(u -> u.path("/document/{id}/download")
                        .queryParam("type", "collapsed")
                        .build(documentId))
                .accept(MediaType.APPLICATION_PDF)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).defaultIfEmpty("<empty>")
                                .flatMap(b -> toError("downloadSignedPdf", r.statusCode().value(), r.statusCode(), b)))
                .bodyToMono(byte[].class)
                .block();
    }
}
