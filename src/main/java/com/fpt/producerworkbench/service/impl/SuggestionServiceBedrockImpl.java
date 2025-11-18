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

    @Value("${aws.ai.song.max-sections:8}")
    private int maxSections;

    @Value("${aws.ai.song.per-section-min:4}")
    private int perSectionMin;

    @Value("${aws.ai.song.baseline.temperature:0.8}")
    private double temperature;

    @Value("${aws.ai.song.min-per-line-ratio:0.85}")
    private double minPerLineRatio;

    @Value("${aws.ai.song.min-total-increase-ratio:0.05}")
    private double minTotalIncreaseRatio;

    @Value("${aws.ai.song.min-total-increase-chars:12}")
    private int minTotalIncreaseChars;

    @Value("${aws.ai.song.max-rewrite-retries:2}")
    private int maxRewriteRetries;


    @EventListener
    public void onLyricsExtracted(LyricsExtractedEvent evt) {
        Track track = trackRepo.findById(evt.getTrackId()).orElse(null);
        if (track == null) return;

        try {
            track.setStatus(TrackStatus.SUGGESTING);
            trackRepo.save(track);

            final String original = Optional.ofNullable(evt.getLyricsText()).orElse("").trim();
            final LyricsShape shape = analyzeLyricsShape(original);
            final List<String> origFlat = flatten(shape.sections);

            SuggestionResult suggestion = rewriteSongLineByLine(original, shape, temperature);

            List<Map<String, Object>> rows = compareLines(origFlat, suggestion.flat);
            Map<String, Object> summary = summarize(rows);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mood", firstNonBlank(suggestion.mood, "gentle"));

            result.put("original", Map.of(
                    "lineCount", origFlat.size(),
                    "sections", toSectionDto(shape.sections),
                    "flat", origFlat
            ));

            result.put("suggestion", Map.of(
                    "mood", suggestion.mood,
                    // targetLines = số dòng gốc (vì rewrite 1-1)
                    "targetLines", origFlat.size(),
                    "temperature", suggestion.temperature,
                    "sections", toSectionDto(suggestion.sections),
                    "flat", suggestion.flat,
                    "lineCount", suggestion.flat.size(),
                    "totalChars", suggestion.flat.stream().mapToInt(this::charCount).sum()
            ));

            result.put("comparison", Map.of(
                    "lines", rows,
                    "summary", summary
            ));

            String finalJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

            track.setAiSuggestions(finalJson);
            track.setStatus(TrackStatus.COMPLETED);
            trackRepo.save(track);

            log.info("[Bedrock] Rewrite-by-line done for track {}. (origLines={}, editedLines={})",
                    track.getId(), origFlat.size(), suggestion.flat.size());

        } catch (Exception e) {
            log.error("[Bedrock] Suggestion error: {}", e.getMessage(), e);
            if (track != null) {
                track.setStatus(TrackStatus.FAILED);
                trackRepo.save(track);
            }
        }
    }

    private SuggestionResult rewriteSongLineByLine(String original, LyricsShape shape, double temperature) throws Exception {
        String prompt = buildRewritePrompt(original, shape);

        int origChars = original.replace("\r", "").length();
        int maxTokens = Math.max(1024, Math.min(6000, origChars / 3 + 512));

        String body = buildAnthropicMessagesBody(prompt, maxTokens, temperature);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                .build();

        log.info("[Bedrock] [rewrite-1-1] invoking modelId={}, maxTokens={}", modelId, maxTokens);
        InvokeModelResponse response = bedrock.invokeModel(request);
        String contentText = extractAnthropicContentText(response.body().asUtf8String());

        SongJson sj = parseSongJson(contentText);

        List<String> origFlat = flatten(shape.sections);
        List<String> editedFlat = flatten(sj.sections);

        if (editedFlat.size() != origFlat.size()) {
            editedFlat = normalizeEditedToOriginal(origFlat, editedFlat);
            sj.sections = rebuildSectionsFromFlat(shape.sections, editedFlat);
        }

        int attempt = 0;
        while (!meetsLengthPolicy_RequireLongerTotal(origFlat, editedFlat, minPerLineRatio) && attempt < maxRewriteRetries) {
            attempt++;
            log.info("[Bedrock] Length policy not met (attempt {}/{}). Expanding...", attempt, maxRewriteRetries);

            SuggestionResult expanded = expandSongToMeetLength(shape, origFlat, editedFlat, Math.max(0.6, temperature - 0.1));
            List<String> expandedFlat = flatten(expanded.sections);

            if (expandedFlat.size() != origFlat.size()) {
                expandedFlat = normalizeEditedToOriginal(origFlat, expandedFlat);
                expanded.sections = rebuildSectionsFromFlat(shape.sections, expandedFlat);
            }
            editedFlat = expandedFlat;
            sj.sections = expanded.sections; // giữ section đã rebuild
        }

        SuggestionResult rs = new SuggestionResult();
        rs.mood = sj.mood;
        rs.sections = sj.sections;
        rs.flat = editedFlat;
        rs.temperature = temperature;
        return rs;
    }

    private String buildRewritePrompt(String lyrics, LyricsShape shape) {
        List<String> outline = new ArrayList<>();
        for (Section s : shape.sections) {
            outline.add("- " + s.label + ": " + s.lines.size() + " dòng (viết lại từng dòng, giữ số lượng & thứ tự)");
        }
        if (outline.isEmpty()) {
            outline.add("- Verse 1: tuỳ số dòng như gốc (viết lại từng dòng)");
        }

        StringBuilder originalBySection = new StringBuilder();
        for (Section s : shape.sections) {
            originalBySection.append("[").append(s.label).append("]\n");
            for (int i = 0; i < s.lines.size(); i++) {
                originalBySection.append(i + 1).append(". ").append(s.lines.get(i)).append("\n");
            }
        }
        if (originalBySection.length() == 0) originalBySection.append(lyrics);

        // Tính ngưỡng tăng tổng ký tự
        int origTotal = flatten(shape.sections).stream().mapToInt(this::charCount).sum();
        int minIncrease = Math.max(1, Math.max(minTotalIncreaseChars, (int) Math.ceil(origTotal * minTotalIncreaseRatio)));

        return """
                Bạn là nhạc sĩ/biên tập lời. Hãy **VIẾT LẠI TỪNG DÒNG** của lời gốc cho mượt mà, nhạc tính, hiện đại hơn,
                nhưng **không thay đổi ý chính**. BẮT BUỘC:
                - Giữ nguyên **số dòng** và **thứ tự dòng** trong mỗi section (1-1).
                - Không gộp, không tách dòng; mỗi dòng gốc tương ứng đúng 1 dòng đã sửa.
                - **TỔNG SỐ KÝ TỰ** của toàn bài đã sửa **PHẢI LỚN HƠN** tổng ký tự bản gốc **tối thiểu %d ký tự** (hoặc ≥ gốc + %.2f%%, lấy giá trị lớn hơn).
                - Nên tăng độ giàu hình ảnh/nhạc tính bằng tính từ, phép so sánh, liên từ mềm mại — nhưng không lan man.
                - Trả về **DUY NHẤT JSON** theo schema:
                  {
                    "mood": "string",
                    "sections": [
                      { "label": "Verse 1", "lines": ["câu 1 đã sửa", "câu 2 đã sửa", "..."] },
                      { "label": "Chorus",  "lines": ["..."] }
                    ]
                  }

                Bố cục & ràng buộc dòng (tham chiếu):
                %s

                LỜI GỐC THEO SECTION (đánh số từng dòng):
                ---
                %s
                ---
                """.formatted(minIncrease, (minTotalIncreaseRatio * 100.0), String.join("\n", outline), originalBySection.toString());
    }

    private boolean meetsLengthPolicy_RequireLongerTotal(List<String> orig, List<String> edited, double minPerLineRatio) {
        int origTotal = orig.stream().mapToInt(this::charCount).sum();
        int editTotal = edited.stream().mapToInt(this::charCount).sum();

        int minIncrease = Math.max(1, Math.max(minTotalIncreaseChars, (int) Math.ceil(origTotal * minTotalIncreaseRatio)));
        if (editTotal < origTotal + minIncrease) return false; // BẮT BUỘC dài hơn

        if (minPerLineRatio > 0) {
            for (int i = 0; i < Math.min(orig.size(), edited.size()); i++) {
                int oc = charCount(orig.get(i));
                int sc = charCount(edited.get(i));
                // Cho phép bỏ qua ràng buộc cho dòng gốc quá ngắn
                if (oc >= 6 && sc + 0.0 < Math.ceil(oc * minPerLineRatio)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<String> normalizeEditedToOriginal(List<String> origFlat, List<String> editedFlat) {
        List<String> out = new ArrayList<>(editedFlat);
        if (out.size() > origFlat.size()) {
            return new ArrayList<>(out.subList(0, origFlat.size()));
        }
        while (out.size() < origFlat.size()) {
            out.add("");
        }
        return out;
    }

    private List<Section> rebuildSectionsFromFlat(List<Section> originalSections, List<String> editedFlat) {
        List<Section> rebuilt = new ArrayList<>();
        int cursor = 0;
        for (Section os : originalSections) {
            Section ns = new Section(os.label);
            for (int i = 0; i < os.lines.size() && cursor < editedFlat.size(); i++) {
                ns.lines.add(editedFlat.get(cursor++));
            }
            rebuilt.add(ns);
        }
        return rebuilt;
    }

    private SuggestionResult expandSongToMeetLength(
            LyricsShape shape,
            List<String> origFlat,
            List<String> editedFlat,
            double temperature
    ) throws Exception {

        List<Integer> minChars = new ArrayList<>(editedFlat.size());
        for (int i = 0; i < editedFlat.size(); i++) {
            int oc = charCount(i < origFlat.size() ? origFlat.get(i) : "");
            int target = (minPerLineRatio > 0) ? (int) Math.ceil(oc * minPerLineRatio) : 0;
            minChars.add(Math.max(0, target));
        }

        int origTotal = origFlat.stream().mapToInt(this::charCount).sum();
        int editTotal = editedFlat.stream().mapToInt(this::charCount).sum();
        int targetTotal = origTotal + Math.max(1, Math.max(minTotalIncreaseChars, (int) Math.ceil(origTotal * minTotalIncreaseRatio)));
        int needTotal = Math.max(0, targetTotal - editTotal); // cần kéo thêm bao nhiêu ký tự

        String prompt = buildExpandPrompt(shape, origFlat, editedFlat, minChars, needTotal, targetTotal, origTotal);

        int baseChars = origFlat.stream().mapToInt(this::charCount).sum();
        int maxTokens = Math.max(1024, Math.min(6000, baseChars / 3 + 512));
        String body = buildAnthropicMessagesBody(prompt, maxTokens, temperature);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromString(body, StandardCharsets.UTF_8))
                .build();

        InvokeModelResponse response = bedrock.invokeModel(request);
        String contentText = extractAnthropicContentText(response.body().asUtf8String());

        SongJson sj = parseSongJson(contentText);
        SuggestionResult rs = new SuggestionResult();
        rs.mood = sj.mood;
        rs.sections = sj.sections;
        rs.flat = flatten(sj.sections);
        rs.temperature = temperature;
        return rs;
    }

    private String buildExpandPrompt(
            LyricsShape shape,
            List<String> origFlat,
            List<String> editedFlat,
            List<Integer> minCharsPerLine,
            int needTotalMoreChars,
            int targetTotalChars,
            int originalTotalChars
    ) {
        StringBuilder pairs = new StringBuilder();
        for (int i = 0; i < editedFlat.size(); i++) {
            int oc = charCount(i < origFlat.size() ? origFlat.get(i) : "");
            int sc = charCount(editedFlat.get(i));
            int minLine = (i < minCharsPerLine.size()) ? minCharsPerLine.get(i) : 0;

            pairs.append((i + 1)).append(". ")
                    .append("{ \"orig\": ").append(jsonEscape(i < origFlat.size() ? origFlat.get(i) : ""))
                    .append(", \"edited\": ").append(jsonEscape(i < editedFlat.size() ? editedFlat.get(i) : ""))
                    .append(", \"origChars\": ").append(oc)
                    .append(", \"editedChars\": ").append(sc)
                    .append(", \"minEditedChars\": ").append(minLine)
                    .append(" }\n");
        }

        StringBuilder outline = new StringBuilder();
        for (Section s : shape.sections) {
            outline.append("- ").append(s.label).append(": ").append(s.lines.size()).append(" dòng\n");
        }

        String totalRule = "- **TỔNG SỐ KÝ TỰ** phải đạt **" + targetTotalChars + "** (gốc = " + originalTotalChars + ")\n" +
                (needTotalMoreChars > 0
                        ? ("- Cần kéo thêm **≥ " + needTotalMoreChars + " ký tự** so với phiên bản đã sửa hiện tại.\n")
                        : "- Đã đạt/tiệm cận tổng mục tiêu, chỉ tinh chỉnh per-line nếu còn thiếu.\n");

        return """
                Bạn là biên tập lời. Nhiệm vụ: **MỞ RỘNG NHẸ** các dòng đã sửa để đạt yêu cầu độ dài,
                vẫn **giữ nguyên ý**, **giữ nhạc tính**, và **GIỮ NGUYÊN SỐ DÒNG & THỨ TỰ** (1-1).
                Không thêm/xoá dòng; chỉ kéo dài hợp lý bằng tính từ, cụm miêu tả, liên từ mềm mại.

                RÀNG BUỘC CỨNG:
                %s
                - Với mỗi dòng i: edited[i] nên có **số ký tự ≥ minEditedChars[i]** (nếu minEditedChars[i] > 0).
                - Giữ nguyên tên section & số dòng từng section.

                Bố cục tham chiếu:
                %s

                DỮ LIỆU:
                (i, orig, edited, origChars, editedChars, minEditedChars)
                ---
                %s
                ---

                Trả về **DUY NHẤT JSON** theo schema:
                {
                  "mood": "string",
                  "sections": [
                    { "label": "Verse 1", "lines": ["...", "..."] },
                    { "label": "Chorus",  "lines": ["...", "..."] }
                  ]
                }
                """.formatted(totalRule, outline.toString(), pairs.toString());
    }


    private String buildAnthropicMessagesBody(String userPrompt, int maxTokens, double temperature) {
        String promptJson = jsonEscape(userPrompt);
        return """
                {
                  "anthropic_version": "bedrock-2023-05-31",
                  "max_tokens": %d,
                  "temperature": %.2f,
                  "system": "Bạn là chuyên gia sáng tác/biên tập, TRẢ VỀ DUY NHẤT JSON theo schema (không có chữ thừa).",
                  "messages": [
                    { "role": "user", "content": [ { "type": "text", "text": %s } ] }
                  ]
                }
                """.formatted(maxTokens, temperature, promptJson);
    }


    private static class SongJson { String mood = ""; List<Section> sections = new ArrayList<>(); }

    private SongJson parseSongJson(String raw) throws Exception {
        SongJson sj = new SongJson();
        String content = raw.trim();

        if (!looksLikeJson(content)) {
            // fallback: bọc free-text thành 1 section, mỗi dòng là 1 câu
            Section s = new Section("Suggestion");
            s.lines = freeTextToLines(content);
            sj.sections = List.of(s);
            return sj;
        }

        JsonNode root = mapper.readTree(content);
        sj.mood = root.path("mood").asText("");

        List<Section> sections = new ArrayList<>();
        JsonNode arr = root.path("sections");
        if (arr.isArray() && !arr.isEmpty()) {
            for (JsonNode n : arr) {
                String label = n.path("label").asText("Verse");
                List<String> lines = new ArrayList<>();
                JsonNode ln = n.path("lines");
                if (ln.isArray()) {
                    for (JsonNode x : ln) {
                        String t = x.asText("").trim();
                        if (!t.isBlank()) lines.add(t);
                    }
                }
                Section s = new Section(label);
                s.lines = lines;
                sections.add(s);
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
                if (!t.isBlank()) { if (sb.length() > 0) sb.append("\n"); sb.append(t); }
            }
        }
        return sb.toString().trim();
    }


    private List<String> flatten(List<Section> sections) {
        List<String> out = new ArrayList<>();
        for (Section s : sections) out.addAll(s.lines);
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
            if (ratio != null) { sumRatio += ratio.doubleValue(); cntRatio++; }
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


    private int charCount(String s) { return (s == null) ? 0 : s.trim().length(); }
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
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
    private List<String> freeTextToLines(String s) {
        String[] arr = s.replace("\r", "").split("\n");
        List<String> out = new ArrayList<>();
        for (String x : arr) { String t = x.trim(); if (!t.isBlank()) out.add(t); }
        return out;
    }

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
        String[] sentences = text.split("(?<=\n|[\\.\\?\\!…])\\s+");
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
    private static class SuggestionResult { String mood=""; double temperature; List<Section> sections=new ArrayList<>(); List<String> flat=new ArrayList<>(); }
}


