package com.fpt.producerworkbench.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.common.TrackStatus;
import com.fpt.producerworkbench.dto.event.LyricsExtractedEvent;
import com.fpt.producerworkbench.dto.request.BeatToLyricsRequest;
import com.fpt.producerworkbench.dto.response.TrackSuggestionResponse;
import com.fpt.producerworkbench.entity.InspirationTrack;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionServiceBedrockImpl {

    private final TrackRepository trackRepo;
    private final BedrockRuntimeClient bedrock;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProjectMemberRepository projectMemberRepo;

    @Value("${bedrock.modelId:anthropic.claude-3-haiku-20240307-v1:0}")
    private String modelId;

    @Value("${aws.ai.song.baseline.temperature:0.8}")
    private double temperature;

    @EventListener
    public void onLyricsExtracted(LyricsExtractedEvent evt) {
        InspirationTrack track = trackRepo.findById(evt.getTrackId()).orElse(null);
        if (track == null) return;

        try {
            track.setStatus(TrackStatus.SUGGESTING);
            trackRepo.save(track);

            final String original = Optional.ofNullable(evt.getLyricsText()).orElse("").trim();
            List<String> originalSections = splitSections(original);

            SongSuggestion suggestion = rewriteWholeSong(originalSections, temperature);

            List<String> origFlat = originalSections;

            List<String> suggFlat = new ArrayList<>();
            for (Section sec : suggestion.sections) {
                if (sec.lines != null && !sec.lines.isEmpty()) {
                    for (String line : sec.lines) {
                        if (line != null && !line.isBlank()) {
                            suggFlat.add(line.trim());
                        }
                    }
                }
            }

            List<Map<String, Object>> rows = compareLines(origFlat, suggFlat);
            Map<String, Object> summary = summarize(rows);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mood", firstNonBlank(suggestion.mood, "gentle"));

            List<Map<String, Object>> origSectionDtos = new ArrayList<>();
            for (int i = 0; i < originalSections.size(); i++) {
                String label = "Section " + (i + 1);
                origSectionDtos.add(Map.of(
                        "label", label,
                        "lines", List.of(originalSections.get(i))
                ));
            }
            result.put("original", Map.of(
                    "lineCount", origFlat.size(),
                    "sections", origSectionDtos,
                    "flat", origFlat
            ));

            List<Map<String, Object>> suggSectionDtos = toSectionDto(suggestion.sections);
            int totalChars = suggFlat.stream().mapToInt(this::charCount).sum();

            Map<String, Object> suggObj = new LinkedHashMap<>();
            suggObj.put("mood", suggestion.mood);
            suggObj.put("temperature", suggestion.temperature);
            suggObj.put("sections", suggSectionDtos);
            suggObj.put("flat", suggFlat);
            suggObj.put("lineCount", suggFlat.size());
            suggObj.put("totalChars", totalChars);

            result.put("suggestion", suggObj);

            result.put("comparison", Map.of(
                    "lines", rows,
                    "summary", summary
            ));

            String finalJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            track.setAiSuggestions(finalJson);
            track.setStatus(TrackStatus.COMPLETED);
            trackRepo.save(track);

            log.info("[Bedrock][whole-song] done for track {} (origSections={}, suggSections={})",
                    track.getId(), origFlat.size(), suggFlat.size());

        } catch (Exception e) {
            log.error("[Bedrock] Suggestion error: {}", e.getMessage(), e);
            track.setStatus(TrackStatus.FAILED);
            trackRepo.save(track);
        }
    }

    private SongSuggestion rewriteWholeSong(List<String> originalSections, double temperature) throws Exception {
        String prompt = buildStructureAwarePrompt(originalSections);
        int origChars = originalSections.stream().mapToInt(s -> s != null ? s.length() : 0).sum();
        int maxTokens = Math.max(1024, Math.min(6000, origChars / 3 + 512));

        String body = buildAnthropicMessagesBody(prompt, maxTokens, temperature);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                .build();

        log.info("[Bedrock][whole-song] invoking modelId={}", modelId);
        InvokeModelResponse response = bedrock.invokeModel(request);
        String contentText = extractAnthropicContentText(response.body().asUtf8String());

        SongSuggestion rs = parseSongJson(contentText);
        rs.temperature = temperature;
        return rs;
    }

    public TrackSuggestionResponse generateLyricsFromBeat(Long userId, Long trackId, BeatToLyricsRequest req) {
        InspirationTrack track = trackRepo.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));

        if (!projectMemberRepo.existsByProject_IdAndUser_Id(track.getProject().getId(), userId)) {
            throw new AppException(ErrorCode.NOT_PROJECT_MEMBER);
        }

        try {
            String prompt = buildCreativePrompt(req);
            double temp = req.getTemperature() != null ? req.getTemperature() : 0.7;

            String body = buildAnthropicMessagesBody(prompt, 2500, temp);

            InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                    .build();

            log.info("[Bedrock] Generating lyrics for trackId={}", trackId);
            InvokeModelResponse response = bedrock.invokeModel(invokeRequest);

            String contentText = extractAnthropicContentText(response.body().asUtf8String());

            SongSuggestion suggestion = parseSongJson(contentText);
            suggestion.temperature = temp;

            String resultJson = convertToResultJson(suggestion, req, track);

            track.setAiSuggestions(resultJson);
            track.setStatus(TrackStatus.COMPLETED);
            trackRepo.save(track);

            return toSuggestionResponse(track);

        } catch (Exception e) {
            log.error("[Bedrock] Error generating lyrics: {}", e.getMessage(), e);
            track.setStatus(TrackStatus.FAILED);
            trackRepo.save(track);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    private String buildCreativePrompt(BeatToLyricsRequest req) {
        List<String> struct = (req.getStructure() != null && !req.getStructure().isEmpty())
                ? req.getStructure()
                : List.of("Verse 1", "Chorus", "Verse 2", "Chorus", "Outro");

        String structString = String.join("\n- ", struct);
        String keywords = (req.getKeywords() != null) ? String.join(", ", req.getKeywords()) : "";

        return """
        You are a professional hit-maker songwriter.
        
        TASK: Write lyrics for a new song based on the following inputs.
        
        INPUTS:
        - Topic: %s
        - Mood: %s
        - Keywords to include: %s
        - Language: Vietnamese
        
        REQUIRED STRUCTURE:
        Please write lyrics strictly following this structure section by section:
        - %s
        
        INSTRUCTIONS:
        - Verse: Usually 4-8 lines, storytelling, lower energy.
        - Chorus: High energy, catchy, emotional peak, repeat key phrases.
        - Bridge: A change in flow or perspective.
        - Ensure the lyrics rhyme well in Vietnamese.
        
        OUTPUT FORMAT (JSON Only):
        {
          "mood": "...",
          "sections": [
            { "label": "Verse 1", "text": "line 1\\nline 2..." },
            { "label": "Chorus", "text": "..." }
          ]
        }
        """.formatted(req.getTopic(), req.getMood(), keywords, structString);
    }

    private String convertToResultJson(SongSuggestion suggestion, BeatToLyricsRequest req, InspirationTrack track) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("mood", suggestion.mood);
        result.put("topic", req.getTopic());
        result.put("temperature", suggestion.temperature);

        if (track.getLyricsText() != null && !track.getLyricsText().isBlank()) {
            List<String> originalSections = splitSections(track.getLyricsText());
            List<Map<String, Object>> origSectionDtos = new ArrayList<>();
            for (int i = 0; i < originalSections.size(); i++) {
                String label = "Section " + (i + 1);
                origSectionDtos.add(Map.of(
                        "label", label,
                        "lines", List.of(originalSections.get(i))
                ));
            }
            result.put("original", Map.of(
                    "lineCount", originalSections.size(),
                    "sections", origSectionDtos,
                    "flat", originalSections
            ));
        }

        List<Map<String, Object>> sectionDtos = new ArrayList<>();
        List<String> flatLines = new ArrayList<>();

        for (Section s : suggestion.sections) {
            sectionDtos.add(Map.of(
                    "label", s.label,
                    "lines", s.lines
            ));
            flatLines.addAll(s.lines);
        }

        result.put("sections", sectionDtos);
        result.put("flat", flatLines);
        result.put("lineCount", flatLines.size());

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private TrackSuggestionResponse toSuggestionResponse(InspirationTrack t) {
        return TrackSuggestionResponse.builder()
                .trackId(t.getId())
                .status(t.getStatus())
                .lyricsText(t.getLyricsText())
                .aiSuggestions(t.getAiSuggestions())
                .transcribeJobName(t.getTranscribeJobName())
                .build();
    }

    private String buildStructureAwarePrompt(List<String> originalSections) {
        int sectionCount = originalSections.size();
        StringBuilder sectionsText = new StringBuilder();
        for (int i = 0; i < sectionCount; i++) {
            sectionsText.append("[Section ").append(i + 1).append("]\n");
            sectionsText.append(originalSections.get(i)).append("\n\n");
        }

        return """
            You are a Vietnamese lyricist and editor.

            TASK:
            - Rewrite the entire original song lyrics in Vietnamese so they sound smoother, more musical and modern,
              but keep the core meaning and emotional tone of each part.
            - Preserve the overall structure of the song:
              * Keep the SAME NUMBER of sections: %d sections in total.
              * Section i in the new version should correspond to section i in the original
                (Section 1 -> new Section 1, Section 2 -> new Section 2, etc.).
              * If the original uses repeated lines or patterns (like a hook/chorus), it is GOOD to keep a similar
                repetition pattern or a slightly varied repetition in your rewrite.
            - Do NOT aggressively compress the song into just a few very short lines. It is fine if the rewrite is
              a bit shorter or longer, but it should still feel like a full song with similar overall length and structure.
            - IMPORTANT: Do NOT output each line separately. Treat each section as one text block and write it into
              a single \"text\" field. You can include line breaks inside the text if you want, but DO NOT create a
              \"lines\" array in the JSON.
            - Language: natural, emotionally rich Vietnamese, suitable for song lyrics.

            OUTPUT FORMAT:
            Return ONLY ONE JSON OBJECT, with NO extra text, exactly in this schema:

            {
              "mood": "short description of the overall mood in English or Vietnamese",
              "sections": [
                {
                  "label": "Section 1",
                  "text": "full rewritten lyrics for section 1, you may include internal line breaks if you want"
                },
                {
                  "label": "Section 2",
                  "text": "full rewritten lyrics for section 2"
                }
              ]
            }

            Do NOT add any other top-level fields. Inside each section object, use ONLY "label" and "text".

            ORIGINAL LYRICS BY SECTION:
            ---------------------------
            %s
            ---------------------------
            """.formatted(sectionCount, sectionsText.toString());
    }

    private String buildAnthropicMessagesBody(String userPrompt, int maxTokens, double temperature) {
        String promptJson = jsonEscape(userPrompt);
        return """
            {
              "anthropic_version": "bedrock-2023-05-31",
              "max_tokens": %d,
              "temperature": %.2f,
              "system": "You are a helpful expert Vietnamese lyricist. ALWAYS return exactly one JSON object that matches the requested schema. No extra commentary.",
              "messages": [
                { "role": "user", "content": [ { "type": "text", "text": %s } ] }
              ]
            }
            """.formatted(maxTokens, temperature, promptJson);
    }

    private static class SongSuggestion {
        String mood = "";
        double temperature;
        List<Section> sections = new ArrayList<>();
    }

    private static class Section {
        String label;
        List<String> lines = new ArrayList<>();

        Section() {
        }

        Section(String label) {
            this.label = label;
        }
    }

    private SongSuggestion parseSongJson(String raw) throws Exception {
        SongSuggestion sj = new SongSuggestion();
        String content = raw.trim();

        int open = content.indexOf("{");
        int close = content.lastIndexOf("}");
        if (open != -1 && close != -1) {
            content = content.substring(open, close + 1);
        }

        if (!looksLikeJson(content)) {
            Section s = new Section("Suggestion");
            s.lines = freeTextToLines(content);
            sj.sections = List.of(s);
            return sj;
        }

        JsonNode root = mapper.readTree(content);
        sj.mood = root.path("mood").asText("");

        List<Section> sections = new ArrayList<>();
        JsonNode arr = root.path("sections");
        if (arr.isArray()) {
            int idx = 1;
            for (JsonNode n : arr) {
                String label = n.path("label").asText("");
                if (label == null || label.isBlank()) label = "Section " + idx;

                Section sec = new Section(label);
                String text = n.path("text").asText("");
                if (text != null && !text.isBlank()) {
                    sec.lines.addAll(freeTextToLines(text));
                } else {
                    JsonNode ln = n.path("lines");
                    if (ln.isArray()) {
                        for(JsonNode x : ln) if(!x.asText("").isBlank()) sec.lines.add(x.asText(""));
                    }
                }

                if (!sec.lines.isEmpty()) {
                    sections.add(sec);
                    idx++;
                }
            }
        }
        if (sections.isEmpty()) {
            Section s = new Section("Suggestion");
            s.lines = freeTextToLines(content);
            sections.add(s);
        }
        sj.sections = sections;
        return sj;
    }

    private String extractAnthropicContentText(String responseJson) throws Exception {
        JsonNode root = mapper.readTree(responseJson);
        JsonNode content = root.path("content");
        if (!content.isArray() || content.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode node : content) {
            if ("text".equals(node.path("type").asText())) {
                String t = node.path("text").asText("");
                if (!t.isBlank()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(t);
                }
            }
        }
        return sb.toString().trim();
    }

    private List<String> splitSections(String lyrics) {
        String normalized = lyrics == null ? "" : lyrics.replace("\r", "").trim();
        if (normalized.isEmpty()) return List.of();
        String[] parts = normalized.split("\\n+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isBlank()) out.add(t);
        }
        return out;
    }

    private List<Map<String, Object>> compareLines(List<String> orig, List<String> sugg) {
        int n = Math.max(orig.size(), sugg.size());
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String o = i < orig.size() ? orig.get(i).trim() : "";
            String s = i < sugg.size() ? sugg.get(i).trim() : "";
            int oc = charCount(o);
            int sc = charCount(s);
            int ow = wordCount(o);
            int sw = wordCount(s);

            String longer;
            if (oc == sc) longer = "equal";
            else longer = (oc > sc) ? "orig" : "sugg";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i + 1);
            row.put("orig", o);
            row.put("sugg", s);
            row.put("origChars", oc);
            row.put("suggChars", sc);
            row.put("delta", sc - oc);
            row.put("ratio", oc == 0 ? null : (double) sc / (double) oc);
            row.put("origWords", ow);
            row.put("suggWords", sw);
            row.put("longer", longer);
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> summarize(List<Map<String, Object>> rows) {
        int origLonger = 0, suggLonger = 0, equal = 0;
        double sumDelta = 0.0, sumRatio = 0.0;
        int cntRatio = 0;

        for (Map<String, Object> r : rows) {
            String longer = String.valueOf(r.get("longer"));
            if ("orig".equals(longer)) origLonger++;
            else if ("sugg".equals(longer)) suggLonger++;
            else equal++;

            Number d = (Number) r.get("delta");
            if (d != null) sumDelta += d.doubleValue();

            Number ratio = (Number) r.get("ratio");
            if (ratio != null) {
                sumRatio += ratio.doubleValue();
                cntRatio++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("origLonger", origLonger);
        m.put("suggLonger", suggLonger);
        m.put("equal", equal);
        m.put("avgDelta", rows.isEmpty() ? 0 : sumDelta / rows.size());
        m.put("avgRatio", cntRatio == 0 ? null : (sumRatio / cntRatio));
        return m;
    }

    private List<Map<String, Object>> toSectionDto(List<Section> sections) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Section s : sections) {
            out.add(Map.of(
                    "label", s.label,
                    "lines", new ArrayList<>(s.lines)
            ));
        }
        return out;
    }

    private String firstNonBlank(String... xs) {
        for (String s : xs) if (s != null && !s.isBlank()) return s;
        return "";
    }

    private int charCount(String s) {
        return (s == null) ? 0 : s.trim().length();
    }

    private int wordCount(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (t.isEmpty()) return 0;
        return t.split("\\s+").length;
    }

    private boolean looksLikeJson(String s) {
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    private String jsonEscape(String s) {
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    private List<String> freeTextToLines(String s) {
        String[] arr = s.replace("\r", "").split("\n");
        List<String> out = new ArrayList<>();
        for (String x : arr) {
            String t = x.trim();
            if (!t.isBlank()) out.add(t);
        }
        return out;
    }
}