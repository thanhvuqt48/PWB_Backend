package com.fpt.producerworkbench.service;

import org.springframework.scheduling.annotation.Async;

public interface TranscribeGcpService {
    @Async("pwbTaskExecutor")
    void startAndPollTranscription(Long trackId);
}
