package com.fpt.producerworkbench.dto.websocket;

import com.fpt.producerworkbench.dto.response.TrackNoteResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event for real-time track note updates via WebSocket
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackNoteEvent {
    
    /**
     * Action type: CREATE, UPDATE, DELETE
     */
    private String action;
    
    /**
     * Track ID that the note belongs to
     */
    private Long trackId;
    
    /**
     * The note data (for CREATE and UPDATE)
     */
    private TrackNoteResponse note;
    
    /**
     * Note ID (for DELETE action)
     */
    private Long noteId;
    
    /**
     * Session ID where the event originated
     */
    private String sessionId;
    
    /**
     * User ID who triggered the action
     */
    private Long triggeredByUserId;
}
