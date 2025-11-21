package com.fpt.producerworkbench.service;

import java.io.File;

public interface FFmpegService {
    
    /**
     * Lấy duration (thời lượng) của file audio bằng ffprobe
     * 
     * @param audioFile File audio cần kiểm tra
     * @return Thời lượng tính bằng giây (làm tròn)
     * @throws RuntimeException nếu có lỗi khi chạy ffprobe
     */
    int getAudioDuration(File audioFile);
    
    /**
     * Trộn voice tag vào master audio mỗi ~25 giây sử dụng FFmpeg
     */
    void mixVoiceTagIntoAudio(File masterFile, File voiceTagFile, File outputFile, int intervalSeconds);
    
    /**
     * Convert audio sang HLS (m3u8 + segments) sử dụng FFmpeg
     */
    void convertToHLS(File inputAudioFile, File outputDir, int segmentDuration);
}



