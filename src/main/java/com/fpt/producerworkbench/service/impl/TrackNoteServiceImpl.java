package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.RoomType;
import com.fpt.producerworkbench.dto.request.CreateTrackNoteRequest;
import com.fpt.producerworkbench.dto.request.UpdateTrackNoteRequest;
import com.fpt.producerworkbench.dto.response.TrackNoteResponse;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.ProjectMember;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.entity.TrackNote;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.TrackNoteMapper;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.TrackMilestoneRepository;
import com.fpt.producerworkbench.repository.TrackNoteRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.TrackNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackNoteServiceImpl implements TrackNoteService {

    private final TrackNoteRepository trackNoteRepository;
    private final TrackMilestoneRepository trackRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TrackNoteMapper trackNoteMapper;

    @Override
    @Transactional
    public TrackNoteResponse createNote(Authentication auth, Long trackId, CreateTrackNoteRequest request) {
        log.info("Tạo ghi chú cho track {}", trackId);

        User currentUser = loadUser(auth);
        Track track = findTrackById(trackId);
        Project project = track.getMilestone().getContract().getProject();

        // Kiểm tra quyền: phải là thành viên của milestone
        checkMemberPermission(currentUser, project, request.getRoomType());

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

        return trackNoteMapper.toDTO(note);
    }

    @Override
    public List<TrackNoteResponse> getNotesByTrack(Authentication auth, Long trackId, RoomType roomType) {
        log.info("Lấy ghi chú cho track {} với roomType {}", trackId, roomType);

        User currentUser = loadUser(auth);
        Track track = findTrackById(trackId);
        Project project = track.getMilestone().getContract().getProject();

        // Kiểm tra quyền xem
        boolean isOwner = isProjectOwner(currentUser, project);

        List<TrackNote> notes;
        if (isOwner && roomType == null) {
            // Owner có thể xem tất cả notes
            notes = trackNoteRepository.findByTrackId(trackId);
        } else if (roomType != null) {
            // Kiểm tra quyền xem theo roomType
            checkMemberPermission(currentUser, project, roomType);
            notes = trackNoteRepository.findByTrackIdAndRoomType(trackId, roomType);
        } else {
            // Không phải owner và không có roomType -> không cho xem
            throw new AppException(ErrorCode.ROOM_TYPE_REQUIRED);
        }

        return trackNoteMapper.toDTOList(notes);
    }

    @Override
    @Transactional
    public TrackNoteResponse updateNote(Authentication auth, Long trackId, Long noteId, UpdateTrackNoteRequest request) {
        log.info("Cập nhật ghi chú {} cho track {}", noteId, trackId);

        User currentUser = loadUser(auth);
        TrackNote note = findNoteById(noteId, trackId);

        // Kiểm tra quyền: chỉ người tạo note được sửa
        if (!note.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.TRACK_NOTE_UPDATE_DENIED);
        }

        note.setContent(request.getContent());
        note = trackNoteRepository.save(note);
        log.info("Đã cập nhật ghi chú {}", noteId);

        return trackNoteMapper.toDTO(note);
    }

    @Override
    @Transactional
    public void deleteNote(Authentication auth, Long trackId, Long noteId) {
        log.info("Xóa ghi chú {} cho track {}", noteId, trackId);

        User currentUser = loadUser(auth);
        TrackNote note = findNoteById(noteId, trackId);
        Track track = note.getTrack();
        Project project = track.getMilestone().getContract().getProject();

        // Kiểm tra quyền: người tạo hoặc owner có thể xóa
        boolean isOwner = isProjectOwner(currentUser, project);
        boolean isNoteCreator = note.getUser().getId().equals(currentUser.getId());

        if (!isOwner && !isNoteCreator) {
            throw new AppException(ErrorCode.TRACK_NOTE_DELETE_DENIED);
        }

        trackNoteRepository.delete(note);
        log.info("Đã xóa ghi chú {}", noteId);
    }

    // ========== Helper Methods ==========

    private User loadUser(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findByEmail(auth.getName())
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

    private boolean isProjectOwner(User user, Project project) {
        return project.getCreator() != null && user.getId().equals(project.getCreator().getId());
    }

    /**
     * Kiểm tra user có quyền xem/tạo note theo roomType
     * - INTERNAL: Owner hoặc COLLABORATOR
     * - CLIENT: Owner hoặc CLIENT
     */
    private void checkMemberPermission(User user, Project project, RoomType roomType) {
        boolean isOwner = isProjectOwner(user, project);
        if (isOwner) {
            return; // Owner có quyền với mọi roomType
        }

        Optional<ProjectMember> memberOpt = projectMemberRepository.findByProjectIdAndUserEmail(
                project.getId(), user.getEmail());

        if (memberOpt.isEmpty()) {
            throw new AppException(ErrorCode.NOT_PROJECT_MEMBER);
        }

        ProjectMember member = memberOpt.get();
        ProjectRole role = member.getProjectRole();

        if (roomType == RoomType.INTERNAL) {
            // Chỉ COLLABORATOR được xem phòng INTERNAL
            if (role != ProjectRole.COLLABORATOR) {
                throw new AppException(ErrorCode.TRACK_NOTE_ACCESS_DENIED);
            }
        } else if (roomType == RoomType.CLIENT) {
            // Chỉ CLIENT được xem phòng CLIENT
            if (role != ProjectRole.CLIENT) {
                throw new AppException(ErrorCode.TRACK_NOTE_ACCESS_DENIED);
            }
        }
    }
}
