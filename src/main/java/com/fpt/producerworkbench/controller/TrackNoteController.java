package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.RoomType;
import com.fpt.producerworkbench.dto.request.CreateTrackNoteRequest;
import com.fpt.producerworkbench.dto.request.UpdateTrackNoteRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.TrackNoteResponse;
import com.fpt.producerworkbench.service.TrackNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tracks/{trackId}/notes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Track Notes", description = "API quản lý ghi chú cho track")
public class TrackNoteController {

    private final TrackNoteService trackNoteService;

    @PostMapping
    @Operation(summary = "Tạo ghi chú cho track")
    public ResponseEntity<ApiResponse<TrackNoteResponse>> createNote(
            Authentication auth,
            @PathVariable Long trackId,
            @Valid @RequestBody CreateTrackNoteRequest request) {
        log.info("POST /api/tracks/{}/notes - roomType: {}", trackId, request.getRoomType());
        TrackNoteResponse response = trackNoteService.createNote(auth, trackId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TrackNoteResponse>builder()
                        .code(201)
                        .message("Tạo ghi chú thành công")
                        .result(response)
                        .build());
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách ghi chú của track")
    public ResponseEntity<ApiResponse<List<TrackNoteResponse>>> getNotes(
            Authentication auth,
            @PathVariable Long trackId,
            @RequestParam(required = false) RoomType roomType) {
        log.info("GET /api/tracks/{}/notes - roomType: {}", trackId, roomType);
        List<TrackNoteResponse> notes = trackNoteService.getNotesByTrack(auth, trackId, roomType);
        return ResponseEntity.ok(ApiResponse.<List<TrackNoteResponse>>builder()
                .code(200)
                .message("Lấy danh sách ghi chú thành công")
                .result(notes)
                .build());
    }

    @PutMapping("/{noteId}")
    @Operation(summary = "Cập nhật ghi chú")
    public ResponseEntity<ApiResponse<TrackNoteResponse>> updateNote(
            Authentication auth,
            @PathVariable Long trackId,
            @PathVariable Long noteId,
            @Valid @RequestBody UpdateTrackNoteRequest request) {
        log.info("PUT /api/tracks/{}/notes/{}", trackId, noteId);
        TrackNoteResponse response = trackNoteService.updateNote(auth, trackId, noteId, request);
        return ResponseEntity.ok(ApiResponse.<TrackNoteResponse>builder()
                .code(200)
                .message("Cập nhật ghi chú thành công")
                .result(response)
                .build());
    }

    @DeleteMapping("/{noteId}")
    @Operation(summary = "Xóa ghi chú")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            Authentication auth,
            @PathVariable Long trackId,
            @PathVariable Long noteId) {
        log.info("DELETE /api/tracks/{}/notes/{}", trackId, noteId);
        trackNoteService.deleteNote(auth, trackId, noteId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Xóa ghi chú thành công")
                .build());
    }
}
