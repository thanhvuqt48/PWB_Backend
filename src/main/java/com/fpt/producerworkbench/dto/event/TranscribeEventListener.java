package com.fpt.producerworkbench.dto.event;

import com.fpt.producerworkbench.service.TranscribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TranscribeEventListener {

    private final TranscribeService transcribeService;

    @EventListener
    public void onAudioUploaded(AudioUploadedEvent evt) {
        transcribeService.startAndPollTranscription(evt.getTrackId());
    }
}
