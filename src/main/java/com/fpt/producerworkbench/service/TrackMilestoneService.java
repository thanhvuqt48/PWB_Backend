package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.TrackCreateRequest;
import com.fpt.producerworkbench.dto.request.TrackDownloadPermissionRequest;
import com.fpt.producerworkbench.dto.request.TrackStatusUpdateRequest;
import com.fpt.producerworkbench.dto.request.TrackUpdateRequest;
import com.fpt.producerworkbench.dto.request.TrackVersionUploadRequest;
import com.fpt.producerworkbench.dto.response.TrackDownloadPermissionResponse;
import com.fpt.producerworkbench.dto.response.TrackResponse;
import com.fpt.producerworkbench.dto.response.TrackUploadUrlResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Service quản lý tracks trong milestone (phòng nội bộ)
 */
public interface TrackMilestoneService {

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

    /**
     * Chủ dự án phê duyệt/từ chối trạng thái track
     * Khi đổi trạng thái sẽ gửi email thông báo cho người chủ track
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @param request Thông tin trạng thái mới và lý do (nếu có)
     * @return Track đã cập nhật
     */
    TrackResponse updateTrackStatus(Authentication auth, Long trackId, TrackStatusUpdateRequest request);

    /**
     * Lấy download URL cho track (bản gốc không có voice tag)
     * Chỉ chủ dự án hoặc users được chỉ định quyền download cho track này mới có quyền download
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @return Presigned download URL
     */
    String getDownloadUrl(Authentication auth, Long trackId);

    /**
     * Chủ dự án cấp/quản lý quyền download cho track
     * Sẽ thay thế toàn bộ danh sách users được cấp quyền bằng danh sách mới
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @param request Danh sách user IDs được cấp quyền download
     */
    void manageDownloadPermissions(Authentication auth, Long trackId, TrackDownloadPermissionRequest request);

    /**
     * Chủ dự án thêm quyền download cho users (không thay thế danh sách hiện có)
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @param request Danh sách user IDs được cấp quyền download
     */
    void grantDownloadPermissions(Authentication auth, Long trackId, TrackDownloadPermissionRequest request);

    /**
     * Chủ dự án hủy quyền download cho user
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @param userId ID của user bị hủy quyền
     */
    void revokeDownloadPermission(Authentication auth, Long trackId, Long userId);

    /**
     * Lấy danh sách users có quyền download track
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @return Danh sách users có quyền download
     */
    TrackDownloadPermissionResponse getDownloadPermissions(Authentication auth, Long trackId);
}




