package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.RoomType;
import com.fpt.producerworkbench.dto.request.CreateTrackNoteRequest;
import com.fpt.producerworkbench.dto.request.UpdateTrackNoteRequest;
import com.fpt.producerworkbench.dto.response.NotePermissionResponse;
import com.fpt.producerworkbench.dto.response.TrackNoteResponse;

import java.util.List;

/**
 * Service quản lý ghi chú cho track
 */
public interface TrackNoteService {

    /**
     * Tạo ghi chú mới cho track
     *
     * @param trackId ID của track
     * @param request Nội dung ghi chú
     * @return TrackNoteResponse
     */
    TrackNoteResponse createNote(Long trackId, CreateTrackNoteRequest request);

    /**
     * Lấy danh sách ghi chú của track theo roomType
     *
     * @param trackId ID của track
     * @param roomType Loại phòng (INTERNAL/CLIENT), null để lấy tất cả
     * @return Danh sách ghi chú
     */
    List<TrackNoteResponse> getNotesByTrack(Long trackId, RoomType roomType);

    /**
     * Cập nhật ghi chú
     *
     * @param trackId ID của track
     * @param noteId ID của ghi chú
     * @param request Nội dung cập nhật
     * @return TrackNoteResponse
     */
    TrackNoteResponse updateNote(Long trackId, Long noteId, UpdateTrackNoteRequest request);

    /**
     * Xóa ghi chú
     *
     * @param trackId ID của track
     * @param noteId ID của ghi chú
     * @param sessionId Session ID for broadcast (optional)
     */
    void deleteNote(Long trackId, Long noteId, String sessionId);

    /**
     * Kiểm tra quyền note của user
     *
     * @param trackId ID của track
     * @return NotePermissionResponse
     */
    NotePermissionResponse checkCanNote(Long trackId);
}
