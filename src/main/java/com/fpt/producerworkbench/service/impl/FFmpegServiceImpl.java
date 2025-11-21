package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.FFmpegService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Implementation của FFmpegService sử dụng ProcessBuilder để chạy ffmpeg/ffprobe CLI
 */
@Service
@Slf4j
public class FFmpegServiceImpl implements FFmpegService {

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${ffprobe.path:ffprobe}")
    private String ffprobePath;

    @Value("${ffmpeg.timeout-seconds:600}")
    private int timeoutSeconds;

    @Value("${ffmpeg.voice-tag.max-repeats:20}")
    private int maxVoiceTagRepeats;

    @Value("${ffmpeg.voice-tag.initial-delay-seconds:25}")
    private int voiceTagInitialDelaySeconds;

    @Value("${ffmpeg.voice-tag.volume-boost:2.0}")
    private double voiceTagVolumeBoost;

    @Override
    public int getAudioDuration(File audioFile) {
        log.info("Lấy duration của file: {}", audioFile.getAbsolutePath());

        List<String> command = List.of(
                ffprobePath,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                audioFile.getAbsolutePath()
        );

        try {
            String output = executeCommand(command, "ffprobe");
            double durationSeconds = Double.parseDouble(output.trim());
            int duration = (int) Math.round(durationSeconds);
            
            log.info("Duration: {} giây", duration);
            return duration;
            
        } catch (Exception e) {
            log.error("Lỗi khi lấy duration: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể lấy duration từ file audio", e);
        }
    }

    @Override
    public void mixVoiceTagIntoAudio(File masterFile, File voiceTagFile, File outputFile, int intervalSeconds) {
        log.info("Bắt đầu mix voice tag vào audio. Master: {}, VoiceTag: {}, Output: {}, Initial delay: {}s, Interval: {}s, Volume boost: {}x",
                masterFile.getName(), voiceTagFile.getName(), outputFile.getName(), voiceTagInitialDelaySeconds, intervalSeconds, voiceTagVolumeBoost);

        // Lấy duration của master để tính số lần chèn tag
        int masterDuration = getAudioDuration(masterFile);
        // Tính số lần chèn tag dựa trên: (masterDuration - initialDelay) / interval
        // Ví dụ: Master 100s, initial 25s, interval 25s → (100-25)/25 = 3 lần
        int effectiveDuration = Math.max(0, masterDuration - voiceTagInitialDelaySeconds);
        int numRepeats = Math.min(effectiveDuration / intervalSeconds + 1, maxVoiceTagRepeats);
        
        log.info("Master duration: {}s, effective duration after initial delay: {}s, sẽ chèn voice tag {} lần", 
                masterDuration, effectiveDuration, numRepeats);

        if (numRepeats == 0 || masterDuration < voiceTagInitialDelaySeconds) {
            log.warn("Master quá ngắn ({}s < initial delay {}s), không chèn voice tag. Copy trực tiếp master.", 
                    masterDuration, voiceTagInitialDelaySeconds);
            copyFile(masterFile, outputFile);
            return;
        }

        // Build filter_complex để chèn voice tag với volume boost
        // Strategy: 
        // 1. Tăng volume của voice tag bằng filter volume
        // 2. Delay voice tag bằng adelay (bắt đầu từ initialDelay + i * interval)
        // 3. Mix tất cả vào master bằng amix
        StringBuilder filterComplex = new StringBuilder();
        List<String> mixInputs = new ArrayList<>();
        mixInputs.add("[0:a]");  // master audio

        for (int i = 0; i < numRepeats; i++) {
            // Voice tag thứ i xuất hiện tại: initialDelay + i * interval
            int delayMs = (voiceTagInitialDelaySeconds + i * intervalSeconds) * 1000;
            String volumeLabel = "vol" + i;
            String tagLabel = "tag" + i;
            
            // Mỗi lần chèn: tăng volume trước, rồi delay
            // Ví dụ: volume=2.0 tương đương +6dB
            // Dùng Locale.US để đảm bảo dấu chấm thập phân (tránh locale vi_VN dùng dấu phẩy)
            filterComplex.append(String.format(Locale.US, "[%d:a]volume=%.2f[%s];", i + 1, voiceTagVolumeBoost, volumeLabel));
            filterComplex.append(String.format(Locale.US, "[%s]adelay=%d|%d[%s];", volumeLabel, delayMs, delayMs, tagLabel));
            mixInputs.add("[" + tagLabel + "]");
        }

        // amix: trộn tất cả inputs
        filterComplex.append(String.join("", mixInputs));
        filterComplex.append(String.format(Locale.US, "amix=inputs=%d:duration=longest:dropout_transition=0[out]", mixInputs.size()));

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");  // overwrite output
        
        command.add("-i");
        command.add(masterFile.getAbsolutePath());
        
        for (int i = 0; i < numRepeats; i++) {
            command.add("-i");
            command.add(voiceTagFile.getAbsolutePath());
        }
        
        command.add("-filter_complex");
        command.add(filterComplex.toString());
        
        command.add("-map");
        command.add("[out]");
        
        // Output format
        command.add("-codec:a");
        command.add("aac");
        command.add("-b:a");
        command.add("192k");
        
        command.add(outputFile.getAbsolutePath());

        try {
            executeCommand(command, "ffmpeg-mix");
            log.info("Mix voice tag thành công: {}", outputFile.getAbsolutePath());
            
        } catch (Exception e) {
            log.error("Lỗi khi mix voice tag: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể mix voice tag vào audio", e);
        }
    }

    @Override
    public void convertToHLS(File inputAudioFile, File outputDir, int segmentDuration) {
        log.info("Convert audio sang HLS. Input: {}, Output dir: {}, Segment duration: {}s",
                inputAudioFile.getName(), outputDir.getAbsolutePath(), segmentDuration);

        // Đảm bảo output directory tồn tại
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new RuntimeException("Không thể tạo output directory: " + outputDir.getAbsolutePath());
        }

        File playlistFile = new File(outputDir, "index.m3u8");
        File segmentPattern = new File(outputDir, "segment_%03d.ts");

        List<String> command = List.of(
                ffmpegPath,
                "-y",
                "-i", inputAudioFile.getAbsolutePath(),
                "-codec:a", "aac",
                "-b:a", "192k",
                "-f", "hls",
                "-hls_time", String.valueOf(segmentDuration),
                "-hls_list_size", "0",
                "-hls_segment_filename", segmentPattern.getAbsolutePath(),
                playlistFile.getAbsolutePath()
        );

        try {
            executeCommand(command, "ffmpeg-hls");
            log.info("Convert HLS thành công. Playlist: {}", playlistFile.getAbsolutePath());
            
        } catch (Exception e) {
            log.error("Lỗi khi convert HLS: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể convert audio sang HLS", e);
        }
    }

    /**
     * Execute command và return stdout
     */
    private String executeCommand(List<String> command, String commandName) throws Exception {
        log.debug("Executing {}: {}", commandName, String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();

        // Đọc output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("{} output: {}", commandName, line);
            }
        }

        // Wait với timeout
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException(commandName + " timeout sau " + timeoutSeconds + " giây");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("{} failed with exit code {}. Output:\n{}", commandName, exitCode, output);
            throw new RuntimeException(commandName + " failed with exit code " + exitCode);
        }

        return output.toString();
    }

    /**
     * Copy file (fallback khi không cần mix)
     */
    private void copyFile(File source, File dest) {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            log.info("Copied file: {} -> {}", source.getName(), dest.getName());
        } catch (IOException e) {
            throw new RuntimeException("Không thể copy file", e);
        }
    }
}



