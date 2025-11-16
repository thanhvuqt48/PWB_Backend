package com.fpt.producerworkbench.service;

import java.io.InputStream;

/**
 * Service trung gian cho Text-to-Speech dùng cho voice tag
 * Interface này tách biệt logic TTS khỏi implementation cụ thể (AWS Polly, Google Cloud TTS, etc)
 */
public interface VoiceTagTtsService {
    
    /**
     * Synthesize voice tag audio từ text
     * 
     * @param text Nội dung voice tag cần chuyển thành giọng nói
     * @return InputStream chứa audio data (MP3 format)
     * @throws RuntimeException nếu có lỗi khi synthesize
     */
    InputStream synthesizeVoiceTag(String text);
}


