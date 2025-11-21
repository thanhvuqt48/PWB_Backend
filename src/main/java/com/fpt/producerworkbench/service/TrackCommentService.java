package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.TrackCommentCreateRequest;
import com.fpt.producerworkbench.dto.request.TrackCommentStatusUpdateRequest;
import com.fpt.producerworkbench.dto.request.TrackCommentUpdateRequest;
import com.fpt.producerworkbench.dto.response.TrackCommentResponse;
import com.fpt.producerworkbench.dto.response.TrackCommentStatisticsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Service quản lý comments trên tracks
 */
public interface TrackCommentService {

    /**
     * Tạo comment mới trên track
     * Gửi email thông báo cho track owner qua Kafka
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @param request Thông tin comment
     * @return Comment đã tạo
     */
    TrackCommentResponse createComment(Authentication auth, Long trackId, TrackCommentCreateRequest request);

    /**
     * Lấy danh sách comment gốc (không có parent) của track với pagination
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @param pageable Phân trang
     * @return Page của comments
     */
    Page<TrackCommentResponse> getRootCommentsByTrack(Authentication auth, Long trackId, Pageable pageable);

    /**
     * Lấy danh sách reply của một comment
     * 
     * @param auth Authentication
     * @param commentId ID của comment cha
     * @return Danh sách reply
     */
    List<TrackCommentResponse> getRepliesByComment(Authentication auth, Long commentId);

    /**
     * Lấy thông tin chi tiết một comment
     * 
     * @param auth Authentication
     * @param commentId ID của comment
     * @return Thông tin comment
     */
    TrackCommentResponse getCommentById(Authentication auth, Long commentId);

    /**
     * Cập nhật nội dung comment
     * Chỉ user tạo comment mới có quyền
     * 
     * @param auth Authentication
     * @param commentId ID của comment
     * @param request Nội dung mới
     * @return Comment đã cập nhật
     */
    TrackCommentResponse updateComment(Authentication auth, Long commentId, TrackCommentUpdateRequest request);

    /**
     * Xóa comment (soft delete)
     * Chỉ user tạo comment hoặc track owner mới có quyền
     * 
     * @param auth Authentication
     * @param commentId ID của comment
     */
    void deleteComment(Authentication auth, Long commentId);

    /**
     * Cập nhật trạng thái của comment
     * Chỉ track owner mới có quyền
     * Gửi email thông báo cho comment owner qua Kafka
     * 
     * @param auth Authentication
     * @param commentId ID của comment
     * @param request Trạng thái mới
     * @return Comment đã cập nhật
     */
    TrackCommentResponse updateCommentStatus(Authentication auth, Long commentId, TrackCommentStatusUpdateRequest request);

    /**
     * Lấy thống kê comment của track theo status
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @return Thống kê
     */
    TrackCommentStatisticsResponse getCommentStatistics(Authentication auth, Long trackId);

    /**
     * Lấy comment theo timestamp cụ thể
     * 
     * @param auth Authentication
     * @param trackId ID của track
     * @param timestamp Timestamp trong track (giây)
     * @return Danh sách comment tại timestamp đó
     */
    List<TrackCommentResponse> getCommentsByTimestamp(Authentication auth, Long trackId, Integer timestamp);

    // ==================== Client Room Comments ====================

    /**
     * Tạo comment mới trong Client Room
     * Gửi email thông báo cho Owner (nếu Client/Observer comment) hoặc Client/Observer (nếu Owner comment)
     * 
     * @param auth Authentication
     * @param deliveryId ID của ClientDelivery
     * @param request Thông tin comment
     * @return Comment đã tạo
     */
    TrackCommentResponse createClientRoomComment(Authentication auth, Long deliveryId, TrackCommentCreateRequest request);

    /**
     * Lấy danh sách comment gốc trong Client Room với pagination
     * 
     * @param auth Authentication
     * @param deliveryId ID của ClientDelivery
     * @param pageable Phân trang
     * @return Page của comments
     */
    Page<TrackCommentResponse> getRootCommentsByClientDelivery(Authentication auth, Long deliveryId, Pageable pageable);

    /**
     * Lấy danh sách reply của một comment trong Client Room
     * 
     * @param auth Authentication
     * @param commentId ID của comment cha
     * @return Danh sách reply
     */
    List<TrackCommentResponse> getClientRoomRepliesByComment(Authentication auth, Long commentId);

    /**
     * Lấy comment theo timestamp cụ thể trong Client Room
     * 
     * @param auth Authentication
     * @param deliveryId ID của ClientDelivery
     * @param timestamp Timestamp trong track (giây)
     * @return Danh sách comment tại timestamp đó
     */
    List<TrackCommentResponse> getClientRoomCommentsByTimestamp(Authentication auth, Long deliveryId, Integer timestamp);

    /**
     * Lấy thống kê comment trong Client Room theo status
     * 
     * @param auth Authentication
     * @param deliveryId ID của ClientDelivery
     * @return Thống kê
     */
    TrackCommentStatisticsResponse getClientRoomCommentStatistics(Authentication auth, Long deliveryId);
}



