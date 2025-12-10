package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.RoomType;
import com.fpt.producerworkbench.dto.request.CreateTrackNoteRequest;
import com.fpt.producerworkbench.dto.request.UpdateTrackNoteRequest;
import com.fpt.producerworkbench.dto.response.TrackNoteResponse;
import com.fpt.producerworkbench.dto.response.NotePermissionResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.entity.TrackNote;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.entity.TrackNote;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.TrackNoteMapper;
import com.fpt.producerworkbench.repository.TrackMilestoneRepository;
import com.fpt.producerworkbench.repository.TrackNoteRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.TrackNoteService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.fpt.producerworkbench.dto.websocket.TrackNoteEvent;
import com.fpt.producerworkbench.service.WebSocketService;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackNoteServiceImpl implements TrackNoteService {

    private final TrackNoteRepository trackNoteRepository;
    private final TrackMilestoneRepository trackRepository;
    private final UserRepository userRepository;
    private final TrackNoteMapper trackNoteMapper;
    private final SecurityUtils securityUtils;
    private final WebSocketService webSocketService;

    @Override
    @Transactional
    public TrackNoteResponse createNote(Long trackId, CreateTrackNoteRequest request) {
        log.info("Tạo ghi chú cho track {}", trackId);

        Long currentUserId = securityUtils.getCurrentUserId();
        User currentUser = findUserById(currentUserId);
        Track track = findTrackById(trackId);
        Project project = track.getMilestone().getContract().getProject();

        // Kiểm tra quyền: chỉ Host hoặc Client của dự án mới note được
        checkNotePermission(currentUser, project);

        // Tạo ghi chú
        TrackNote note = TrackNote.builder()
                .track(track)
                .user(currentUser)
                .content(request.getContent())
                .roomType(request.getRoomType())
                .timestamp(request.getTimestamp())
                .build();

        note = trackNoteRepository.save(note);
        log.info("Đã tạo ghi chú {} cho track {}", note.getId(), trackId);

        TrackNoteResponse response = trackNoteMapper.toDTO(note);

        // Broadcast real-time nếu có sessionId
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            broadcastNoteEvent("CREATE", trackId, response, null, request.getSessionId(), currentUserId);
        }

        return response;
    }

    @Override
    public List<TrackNoteResponse> getNotesByTrack(Long trackId, RoomType roomType) {
        log.info("Lấy ghi chú cho track {} với roomType {}", trackId, roomType);

        Long currentUserId = securityUtils.getCurrentUserId();
        User currentUser = findUserById(currentUserId);
        Track track = findTrackById(trackId);
        Project project = track.getMilestone().getContract().getProject();

        // Kiểm tra quyền xem
        checkNotePermission(currentUser, project);

        List<TrackNote> notes;
        boolean isHost = isProjectHost(currentUser, project);
        
        if (isHost && roomType == null) {
            // Host có thể xem tất cả notes
            notes = trackNoteRepository.findByTrackId(trackId);
        } else if (roomType != null) {
            notes = trackNoteRepository.findByTrackIdAndRoomType(trackId, roomType);
        } else {
            // Client chỉ xem được CLIENT room
            notes = trackNoteRepository.findByTrackIdAndRoomType(trackId, RoomType.CLIENT);
        }

        return trackNoteMapper.toDTOList(notes);
    }

    @Override
    @Transactional
    public TrackNoteResponse updateNote(Long trackId, Long noteId, UpdateTrackNoteRequest request) {
        log.info("Cập nhật ghi chú {} cho track {}", noteId, trackId);

        Long currentUserId = securityUtils.getCurrentUserId();
        TrackNote note = findNoteById(noteId, trackId);

        // Kiểm tra quyền: chỉ người tạo note được sửa
        if (!note.getUser().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.TRACK_NOTE_UPDATE_DENIED);
        }

        note.setContent(request.getContent());
        note = trackNoteRepository.save(note);
        log.info("Đã cập nhật ghi chú {}", noteId);

        TrackNoteResponse response = trackNoteMapper.toDTO(note);

        // Broadcast real-time nếu có sessionId
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            broadcastNoteEvent("UPDATE", trackId, response, null, request.getSessionId(), currentUserId);
        }

        return response;
    }

    @Override
    @Transactional
    public void deleteNote(Long trackId, Long noteId, String sessionId) {
        log.info("Xóa ghi chú {} cho track {}", noteId, trackId);

        Long currentUserId = securityUtils.getCurrentUserId();
        User currentUser = findUserById(currentUserId);
        TrackNote note = findNoteById(noteId, trackId);
        Track track = note.getTrack();
        Project project = track.getMilestone().getContract().getProject();

        // Kiểm tra quyền: người tạo hoặc Host có thể xóa
        boolean isHost = isProjectHost(currentUser, project);
        boolean isNoteCreator = note.getUser().getId().equals(currentUserId);

        if (!isHost && !isNoteCreator) {
            throw new AppException(ErrorCode.TRACK_NOTE_DELETE_DENIED);
        }

        trackNoteRepository.delete(note);
        log.info("Đã xóa ghi chú {}", noteId);

        // Broadcast real-time nếu có sessionId
        if (sessionId != null && !sessionId.isBlank()) {
            broadcastNoteEvent("DELETE", trackId, null, noteId, sessionId, currentUserId);
        }
    }

    @Override
    public NotePermissionResponse checkCanNote(Long trackId) {
        log.info("Kiểm tra quyền note cho track {}", trackId);

        Long currentUserId = securityUtils.getCurrentUserId();
        User currentUser = findUserById(currentUserId);
        Track track = findTrackById(trackId);
        Project project = track.getMilestone().getContract().getProject();

        boolean isHost = isProjectHost(currentUser, project);
        boolean isClient = isProjectClient(currentUser, project);
        boolean canNote = isHost || isClient;

        return NotePermissionResponse.builder()
                .canNote(canNote)
                .isHost(isHost)
                .isClient(isClient)
                .build();
    }

    // ========== Helper Methods ==========

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private Track findTrackById(Long trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException(ErrorCode.TRACK_NOT_FOUND));
    }

    private TrackNote findNoteById(Long noteId, Long trackId) {
        return trackNoteRepository.findByIdAndTrackId(noteId, trackId)
                .orElseThrow(() -> new AppException(ErrorCode.TRACK_NOTE_NOT_FOUND));
    }

    /**
     * Check if user is the project host (creator/owner)
     */
    private boolean isProjectHost(User user, Project project) {
        return project.getCreator() != null && user.getId().equals(project.getCreator().getId());
    }

    /**
     * Check if user is the project client
     */
    private boolean isProjectClient(User user, Project project) {
        return project.getClient() != null && user.getId().equals(project.getClient().getId());
    }

    /**
     * Kiểm tra user có quyền note không
     * - Host (project creator) có quyền
     * - Client (project client) có quyền
     */
    private void checkNotePermission(User user, Project project) {
        boolean isHost = isProjectHost(user, project);
        boolean isClient = isProjectClient(user, project);

        if (!isHost && !isClient) {
            throw new AppException(ErrorCode.TRACK_NOTE_ACCESS_DENIED);
        }
    }

    /**
     * Broadcast track note event to WebSocket subscribers
     */
    private void broadcastNoteEvent(String action, Long trackId, TrackNoteResponse note, Long noteId, String sessionId, Long triggeredByUserId) {
        try {
            TrackNoteEvent event = TrackNoteEvent.builder()
                    .action(action)
                    .trackId(trackId)
                    .note(note)
                    .noteId(noteId)
                    .sessionId(sessionId)
                    .triggeredByUserId(triggeredByUserId)
                    .build();
            webSocketService.broadcastTrackNoteEvent(sessionId, event);
            log.info("Broadcasted track note event: {} for track {} in session {}", action, trackId, sessionId);
        } catch (Exception e) {
            log.error("Failed to broadcast track note event: {}", e.getMessage());
        }
    }
}
