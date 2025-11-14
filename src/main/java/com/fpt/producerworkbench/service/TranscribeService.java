package com.fpt.producerworkbench.service;

public interface TranscribeService {
    void startAndPollTranscription(Long trackId);
}
