package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.VoiceTagTtsService;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Implementation của VoiceTagTtsService sử dụng Google Cloud Text-to-Speech
 * Support tiếng Việt (vi-VN) và volume control cho voice tag
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleCloudTtsServiceImpl implements VoiceTagTtsService {

        private final GoogleCredentials googleCredentials;

        @Value("${gcp.tts.language-code:vi-VN}")
        private String languageCode;

        @Value("${gcp.tts.voice-name:vi-VN-Wavenet-A}")
        private String voiceName;

        @Value("${gcp.tts.speaking-rate:1.0}")
        private double speakingRate;

        @Value("${gcp.tts.pitch:0.0}")
        private double pitch;

        @Value("${gcp.tts.volume-gain-db:6.0}")
        private double volumeGainDb;

        @Override
        public InputStream synthesizeVoiceTag(String text) {
                log.info("Bắt đầu synthesize voice tag với Google Cloud TTS. Text length: {}", text.length());
                log.info("Config: language={}, voice={}, rate={}, pitch={}, volume={}dB",
                                languageCode, voiceName, speakingRate, pitch, volumeGainDb);

                try {
                        TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()
                                        .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
                                        .build();

                        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create(settings)) {

                                // Set input text
                                SynthesisInput input = SynthesisInput.newBuilder()
                                                .setText(text)
                                                .build();

                                // Build voice selection with Vietnamese language
                                VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                                                .setLanguageCode(languageCode)
                                                .setName(voiceName)
                                                .build();

                                // Build audio config with volume gain
                                AudioConfig audioConfig = AudioConfig.newBuilder()
                                                .setAudioEncoding(AudioEncoding.MP3)
                                                .setSpeakingRate(speakingRate)
                                                .setPitch(pitch)
                                                .setVolumeGainDb(volumeGainDb)
                                                .build();

                                // Perform TTS request
                                SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(
                                                input, voice, audioConfig);

                                // Get audio content
                                ByteString audioContents = response.getAudioContent();
                                byte[] audioBytes = audioContents.toByteArray();

                                log.info("Google Cloud TTS synthesize thành công. Voice: {}, Language: {}, size: {} bytes",
                                                voiceName, languageCode, audioBytes.length);

                                return new ByteArrayInputStream(audioBytes);
                        }

                } catch (Exception e) {
                        log.error("Lỗi khi gọi Google Cloud TTS: {}", e.getMessage(), e);
                        throw new RuntimeException("Không thể tạo voice tag từ Google Cloud TTS: " + e.getMessage(), e);
                }
        }
}
