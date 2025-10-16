package com.fpt.producerworkbench.common;

public enum ParticipantRole {
    OWNER,        // Host/Producer (full control)
    CLIENT,       // Client (can speak/video, limited control)
    COLLABORATOR, // Collaborator (can speak/video, some control)
    OBSERVER      // Observer (view only, no audio/video)
}