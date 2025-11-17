package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.TrackCreateRequest;
import com.fpt.producerworkbench.dto.request.TrackUpdateRequest;
import com.fpt.producerworkbench.dto.request.TrackVersionUploadRequest;
import com.fpt.producerworkbench.dto.response.TrackResponse;
import com.fpt.producerworkbench.dto.response.TrackUploadUrlResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Service quản lý tracks trong milestone (phòng nội bộ)
 */
public interface TrackService {

    /**
     * Tạo track mới và trả về presigned URL để upload file master
     * 
     * @param auth Authentication
     * @param projectId ID của project
     * @param milestoneId ID của milestone
     * @param request Thông tin track
     * @return TrackUploadUrlResponse chứa trackId và presigned URL
     */
    TrackUploadUrlResponse createTrack(Authentication auth, Long projectId, Long milestoneId, TrackCreateRequest request);

    /**
     * Hoàn tất upload và trigger xử lý audio
     * 
     * @param auth Authentication
     * @param trackId ID của track
     */
    void finalizeUpload(Authentication auth, Long trackId);

    /**
     * Lấy danh sách tracks trong milestone
     * 
     * @param auth Authentication
     * @param milestoneId ID của milestone
     * @return Danh sách tracks
     */
    List<TrackResponse> getTracksByMilestone(Authentication auth, Long milestoneId);

    /**
     * Lấy thông tin chi tiết một track
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @return Thông tin track
     */
    TrackResponse getTrackById(Authentication auth, Long trackId);

    /**
     * Cập nhật thông tin track
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @param request Thông tin cập nhật
     * @return Track đã cập nhật
     */
    TrackResponse updateTrack(Authentication auth, Long trackId, TrackUpdateRequest request);

    /**
     * Xóa track
     * 
     * @param auth Authentication
     * @param trackId ID của track
     */
    void deleteTrack(Authentication auth, Long trackId);

    /**
     * Lấy HLS playback URL để phát track preview
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @return HLS playback URL (presigned)
     */
    String getPlaybackUrl(Authentication auth, Long trackId);

    /**
     * Upload version mới của một track hiện có
     * Version sẽ tự động tăng dựa trên các version hiện có của track đó
     * 
     * @param auth Authentication
     * @param trackId ID của track gốc (version đầu tiên)
     * @param request Thông tin version mới
     * @return TrackUploadUrlResponse chứa trackId và presigned URL
     */
    TrackUploadUrlResponse uploadNewVersion(Authentication auth, Long trackId, TrackVersionUploadRequest request);
}




