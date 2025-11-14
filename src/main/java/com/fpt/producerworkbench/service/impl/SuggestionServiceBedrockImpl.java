package com.fpt.producerworkbench.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.common.TrackStatus;
import com.fpt.producerworkbench.dto.event.LyricsExtractedEvent;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionServiceBedrockImpl {

    private final TrackRepository trackRepo;
    private final BedrockRuntimeClient bedrock;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${bedrock.modelId:anthropic.claude-3-haiku-20240307-v1:0}")
    private String modelId;

    @Value("${aws.ai.suggestion-mode:BY_SONG}")
    private String suggestionMode;

    @Value("${aws.ai.num-suggestions:3}")
    private int numSuggestions;

    @Value("${aws.ai.song.scale-factor:1.0}")
    private double scaleFactor;

    @Value("${aws.ai.song.max-sections:8}")
    private int maxSections;

    @Value("${aws.ai.song.min-lines:24}")
    private int minLines;

    @Value("${aws.ai.song.per-section-min:4}")
    private int perSectionMin;

    @EventListener
    public void onLyricsExtracted(LyricsExtractedEvent evt) {
        Track track = trackRepo.findById(evt.getTrackId()).orElse(null);
        if (track == null) return;

        try {
            track.setStatus(TrackStatus.SUGGESTING);
            trackRepo.save(track);

            final String lyrics = Optional.ofNullable(evt.getLyricsText()).orElse("").trim();
            final LyricsShape shape = analyzeLyricsShape(lyrics);

            String prompt;
            String body;
            if ("BY_SONG".equalsIgnoreCase(suggestionMode)) {
                prompt = buildSongPrompt(lyrics, shape);
                body = buildAnthropicMessagesBody(prompt, 3072, 0.8); // nới max_tokens cho bài dài
            } else {
                prompt = buildCountPrompt(lyrics, numSuggestions);
                body = buildAnthropicMessagesBody(prompt, 1024, 0.9);
            }

            log.info("[AI] linesIn={}, sectionsIn={}, target>=minLines={}",
                    shape.totalLines, shape.sections.size(), minLines);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                    .build();

            log.info("[Bedrock] Invoking serverless modelId: {}", modelId);
            InvokeModelResponse response = bedrock.invokeModel(request);

            String contentText = extractAnthropicContentText(response.body().asUtf8String());

            String finalJson = looksLikeJson(contentText)
                    ? normalizeSongJson(contentText)
                    : toFallbackJson(contentText);

            track.setAiSuggestions(finalJson);
            track.setStatus(TrackStatus.COMPLETED);
            trackRepo.save(track);

            log.info("[Bedrock] Suggestion(BY_SONG={}) done for track {}.",
                    "BY_SONG".equalsIgnoreCase(suggestionMode), track.getId());

        } catch (Exception e) {
            log.error("[Bedrock] Suggestion error: {}", e.getMessage(), e);
            track.setStatus(TrackStatus.FAILED);
            trackRepo.save(track);
        }
    }


    /** BY_SONG: ép tối thiểu số dòng & giữ cấu trúc */
    private String buildSongPrompt(String lyrics, LyricsShape shape) {
        int estimated = shape.totalLines;
        int targetLines = Math.max(minLines,
                (int) Math.round(estimated * clamp(scaleFactor, 0.8, 1.4)));

        StringBuilder sectionGuide = new StringBuilder();
        if (!shape.sections.isEmpty()) {
            int take = Math.min(shape.sections.size(), maxSections);
            for (int i = 0; i < take; i++) {
                Section s = shape.sections.get(i);
                int hint = Math.max(perSectionMin, s.lines.size()); // gợi ý mỗi phần
                sectionGuide.append("- ").append(s.label).append(" (~").append(hint).append(" dòng)\n");
            }
        } else {
            sectionGuide
                    .append("- Verse (~").append(Math.max(perSectionMin, targetLines / 2)).append(" dòng)\n")
                    .append("- Chorus (~").append(Math.max(perSectionMin, targetLines / 2)).append(" dòng)\n");
        }

        return """
                Bạn là chuyên gia sáng tác nhạc. Dựa trên LỜI GỐC dưới đây, hãy tạo **một bài lời mới hoàn chỉnh**
                giữ **mood/chủ đề/ngôn ngữ** như bản gốc, tránh lặp nguyên văn.

                RÀNG BUỘC BẮT BUỘC:
                - **TỔNG SỐ DÒNG ≥ %d** (nếu thiếu, hãy MỞ RỘNG nội dung cho đủ).
                - Giữ cấu trúc section tương tự (nếu có), gợi ý:
                  %s
                - Mỗi section nên có **≥ %d dòng** (có thể nhiều hơn).
                - Trả về **JSON THUẦN duy nhất**, đúng schema:
                  {
                    "mood": "string",
                    "sections": [
                      { "label": "Verse 1", "lines": ["...", "..."] },
                      { "label": "Chorus",  "lines": ["...", "..."] }
                    ],
                    "notes": "ghi chú ngắn (tuỳ chọn)"
                  }

                LỜI GỐC:
                ---
                %s
                ---
                """.formatted(targetLines, sectionGuide, perSectionMin, lyrics);
    }

    /** BY_COUNT: trả về JSON có mảng suggestions */
    private String buildCountPrompt(String lyrics, int k) {
        return """
                Bạn là chuyên gia sáng tác nhạc. Dựa trên LỜI GỐC sau:
                ---
                %s
                ---
                Hãy gợi ý %d đoạn lời hát ngắn (2-4 dòng/đoạn), giữ mood/chủ đề tương tự, tránh lặp nguyên văn.
                Trả về **JSON THUẦN**:
                {
                  "mood": "string",
                  "suggestions": ["...", "..."]
                }
                """.formatted(lyrics, k);
    }

    private String buildAnthropicMessagesBody(String userPrompt, int maxTokens, double temperature) {
        String promptJson = jsonEscape(userPrompt);
        return """
                {
                  "anthropic_version": "bedrock-2023-05-31",
                  "max_tokens": %d,
                  "temperature": %.2f,
                  "system": "Bạn là chuyên gia sáng tác, trả lời đúng định dạng JSON duy nhất.",
                  "messages": [
                    { "role": "user", "content": [ { "type": "text", "text": %s } ] }
                  ]
                }
                """.formatted(maxTokens, temperature, promptJson);
    }


    /** Lấy text từ content[] (định dạng phản hồi Anthropic trên Bedrock) */
    private String extractAnthropicContentText(String responseJson) throws Exception {
        JsonNode root = mapper.readTree(responseJson);
        JsonNode content = root.path("content");
        if (!content.isArray() || content.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode node : content) {
            if ("text".equals(node.path("type").asText())) {
                String t = node.path("text").asText("");
                if (!t.isBlank()) { if (sb.length() > 0) sb.append("\n"); sb.append(t); }
            }
        }
        return sb.toString().trim();
    }

    private boolean looksLikeJson(String s) {
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    private String normalizeSongJson(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        if (root.has("suggestions")) {
            JsonNode sugg = root.get("suggestions");
            Map<String, Object> alt = new LinkedHashMap<>();
            alt.put("mood", root.path("mood").asText(""));
            List<Map<String, Object>> sections = new ArrayList<>();
            Map<String, Object> sec = new LinkedHashMap<>();
            sec.put("label", "Suggestions");
            List<String> lines = new ArrayList<>();
            for (JsonNode x : sugg) lines.add(x.asText());
            sec.put("lines", lines);
            sections.add(sec);
            alt.put("sections", sections);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(alt);
        }
        if (root.has("sections")) {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        }
        return toFallbackJson(json);
    }

    private String toFallbackJson(String freeText) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mood", "");
        Map<String, Object> sec = new LinkedHashMap<>();
        sec.put("label", "Suggestion");
        sec.put("lines", freeTextToLines(freeText));
        m.put("sections", List.of(sec));
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(m);
    }

    private List<String> freeTextToLines(String s) {
        String[] arr = s.replace("\r", "").split("\n");
        List<String> out = new ArrayList<>();
        for (String x : arr) { String t = x.trim(); if (!t.isBlank()) out.add(t); }
        return out;
    }

    private String jsonEscape(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
    }

    private double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }


    private static final Pattern SECTION_BRACKET = Pattern.compile(
            "^\\s*\\[(hook(?:-?intro)?|intro|verse|pre[- ]?chorus|chorus|rap(?:-?bridge)?|bridge|outro|final\\s+chorus)(\\s*\\d+)?]\\s*:?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SECTION_COLON = Pattern.compile(
            "^\\s*(hook(?:-?intro)?|intro|verse\\s*\\d*|pre[- ]?chorus|chorus|rap(?:-?bridge)?|bridge|outro|final\\s+chorus)\\s*:?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private LyricsShape analyzeLyricsShape(String lyrics) {
        String normalized = lyrics == null ? "" : lyrics.replace("\r", "").trim();
        List<String> lines = normalized.contains("\n")
                ? Arrays.asList(normalized.split("\n"))
                : splitToPseudoLines(normalized);

        List<Section> sections = new ArrayList<>();
        Section cur = new Section("Verse 1");
        int nonEmpty = 0;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isBlank()) {
                if (!cur.lines.isEmpty()) { sections.add(cur); cur = new Section("Verse " + (sections.size() + 1)); }
                continue;
            }
            nonEmpty++;

            if (SECTION_BRACKET.matcher(line).matches() || SECTION_COLON.matcher(line).matches()) {
                if (!cur.lines.isEmpty()) sections.add(cur);
                cur = new Section(normalizeHeading(line));
            } else {
                cur.lines.add(line);
            }
        }
        if (!cur.lines.isEmpty()) sections.add(cur);
        if (sections.isEmpty()) sections.add(new Section("Verse 1"));

        LyricsShape shape = new LyricsShape();
        shape.totalLines = nonEmpty;
        shape.sections = sections;
        return shape;
    }

    private String normalizeHeading(String tag) {
        String t = tag.replace("[", "").replace("]", "").replace(":", "").trim().toLowerCase();
        t = t.replaceAll("\\s+", " ").replace("-", " ");
        if (t.matches("verse\\s*\\d+")) return t.substring(0,1).toUpperCase() + t.substring(1);
        return switch (t) {
            case "hook intro", "hookintro", "intro" -> "Hook-Intro";
            case "pre chorus", "prechorus"         -> "Pre-Chorus";
            case "chorus", "final chorus"          -> Character.toUpperCase(t.charAt(0)) + t.substring(1);
            case "rap", "rap bridge", "rapbridge"  -> "Rap-Bridge";
            case "bridge"                          -> "Bridge";
            case "outro"                           -> "Outro";
            default                                -> Character.toUpperCase(t.charAt(0)) + t.substring(1);
        };
    }

    private List<String> splitToPseudoLines(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        String[] sentences = text.split("(?<=[\\.\\?\\!…])\\s+");
        for (String s : sentences) {
            String[] words = s.trim().split("\\s+");
            if (words.length == 0) continue;
            StringBuilder line = new StringBuilder();
            int cnt = 0;
            for (String w : words) {
                if (line.length() > 0) line.append(' ');
                line.append(w);
                if (++cnt >= 10) { out.add(line.toString().trim()); line.setLength(0); cnt = 0; }
            }
            if (line.length() > 0) out.add(line.toString().trim());
        }
        return out;
    }

    private static class LyricsShape { int totalLines; List<Section> sections = new ArrayList<>(); }
    private static class Section { String label; List<String> lines = new ArrayList<>(); Section(String l){label=l;} }
}
