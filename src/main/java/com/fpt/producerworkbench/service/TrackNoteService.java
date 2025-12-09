package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.RoomType;
import com.fpt.producerworkbench.dto.request.CreateTrackNoteRequest;
import com.fpt.producerworkbench.dto.request.UpdateTrackNoteRequest;
import com.fpt.producerworkbench.dto.response.TrackNoteResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Service quản lý ghi chú cho track
 */
public interface TrackNoteService {

    /**
     * Tạo ghi chú mới cho track
     *
     * @param auth Authentication
     * @param trackId ID của track
     * @param request Nội dung ghi chú
     * @return TrackNoteResponse
     */
    TrackNoteResponse createNote(Authentication auth, Long trackId, CreateTrackNoteRequest request);

    /**
     * Lấy danh sách ghi chú của track theo roomType
     *
     * @param auth Authentication
     * @param trackId ID của track
     * @param roomType Loại phòng (INTERNAL/CLIENT), null để lấy tất cả
     * @return Danh sách ghi chú
     */
    List<TrackNoteResponse> getNotesByTrack(Authentication auth, Long trackId, RoomType roomType);

    /**
     * Cập nhật ghi chú
     *
     * @param auth Authentication
     * @param trackId ID của track
     * @param noteId ID của ghi chú
     * @param request Nội dung cập nhật
     * @return TrackNoteResponse
     */
    TrackNoteResponse updateNote(Authentication auth, Long trackId, Long noteId, UpdateTrackNoteRequest request);

    /**
     * Xóa ghi chú
     *
     * @param auth Authentication
     * @param trackId ID của track
     * @param noteId ID của ghi chú
     */
    void deleteNote(Authentication auth, Long trackId, Long noteId);
}
