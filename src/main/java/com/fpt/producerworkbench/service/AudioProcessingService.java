package com.fpt.producerworkbench.service;

/**
 * Service xử lý audio cho track
 * - Text-to-Speech (TTS) để tạo voice tag
 * - Trộn voice tag vào nhạc định kỳ (~25 giây)
 * - Chuyển đổi audio sang HLS format
 */
public interface AudioProcessingService {

    /**
     * Xử lý audio cho track: TTS + voice tag mixing + HLS conversion
     * Method này sẽ được gọi sau khi user upload xong file master hoặc khi re-process
     * 
     * @param trackId ID của track cần xử lý
     */
    void processTrackAudio(Long trackId);

    /**
     * Tạo file audio voice tag từ text bằng TTS
     * 
     * @param text Nội dung voice tag
     * @param trackId ID của track
     * @return S3 key của file voice tag audio đã tạo
     */
    String generateVoiceTagAudio(String text, Long trackId);

    /**
     * Trộn voice tag vào master audio với chu kỳ định kỳ (~25 giây)
     * 
     * @param masterS3Key S3 key của file master
     * @param voiceTagS3Key S3 key của file voice tag
     * @param trackId ID của track
     * @return S3 key của file audio đã trộn voice tag
     */
    String mixVoiceTagIntoAudio(String masterS3Key, String voiceTagS3Key, Long trackId);

    /**
     * Chuyển đổi audio sang HLS format (playlist + segments)
     * 
     * @param audioS3Key S3 key của file audio (master hoặc đã trộn voice tag)
     * @param trackId ID của track
     * @return S3 prefix của thư mục HLS
     */
    String convertToHls(String audioS3Key, Long trackId);

    /**
     * Lấy thời lượng của audio file (seconds)
     * 
     * @param audioS3Key S3 key của file audio
     * @return Thời lượng (seconds)
     */
    Integer getAudioDuration(String audioS3Key);
}


