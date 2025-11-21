package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.CommentStatus;
import com.fpt.producerworkbench.entity.TrackComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackCommentRepository extends JpaRepository<TrackComment, Long> {

    /**
     * Lấy tất cả comment gốc (không có parent) của một track trong Internal Room
     * Sắp xếp theo timestamp tăng dần để hiển thị theo thứ tự timeline
     * Chỉ lấy comment không có clientDelivery (Internal Room)
     */
    @Query("SELECT tc FROM TrackComment tc WHERE tc.track.id = :trackId " +
           "AND tc.clientDelivery IS NULL " +
           "AND tc.parentComment IS NULL AND tc.isDeleted = false " +
           "ORDER BY tc.timestamp ASC, tc.createdAt ASC")
    Page<TrackComment> findRootCommentsByTrackId(@Param("trackId") Long trackId, Pageable pageable);

    /**
     * Lấy tất cả reply của một comment (trong Internal Room)
     * Chỉ lấy reply không có clientDelivery
     */
    @Query("SELECT tc FROM TrackComment tc WHERE tc.parentComment.id = :parentCommentId " +
           "AND tc.clientDelivery IS NULL " +
           "AND tc.isDeleted = false ORDER BY tc.createdAt ASC")
    List<TrackComment> findRepliesByParentCommentId(@Param("parentCommentId") Long parentCommentId);

    /**
     * Đếm số lượng reply của một comment
     */
    @Query("SELECT COUNT(tc) FROM TrackComment tc WHERE tc.parentComment.id = :parentCommentId " +
           "AND tc.isDeleted = false")
    Long countRepliesByParentCommentId(@Param("parentCommentId") Long parentCommentId);

    /**
     * Tìm comment theo ID và không bị xóa
     */
    @Query("SELECT tc FROM TrackComment tc WHERE tc.id = :id AND tc.isDeleted = false")
    Optional<TrackComment> findByIdAndNotDeleted(@Param("id") Long id);

    /**
     * Lấy tất cả comment của một track theo status
     */
    @Query("SELECT tc FROM TrackComment tc WHERE tc.track.id = :trackId " +
           "AND tc.status = :status AND tc.isDeleted = false " +
           "ORDER BY tc.createdAt DESC")
    Page<TrackComment> findByTrackIdAndStatus(@Param("trackId") Long trackId, 
                                               @Param("status") CommentStatus status, 
                                               Pageable pageable);

    /**
     * Lấy tất cả comment của một user trên một track
     */
    @Query("SELECT tc FROM TrackComment tc WHERE tc.track.id = :trackId " +
           "AND tc.user.id = :userId AND tc.isDeleted = false " +
           "ORDER BY tc.createdAt DESC")
    List<TrackComment> findByTrackIdAndUserId(@Param("trackId") Long trackId, 
                                               @Param("userId") Long userId);

    /**
     * Đếm số lượng comment của một track theo status
     * @deprecated Sử dụng countByTrackIdAndStatusInternal để chỉ đếm Internal Room comments
     */
    @Query("SELECT COUNT(tc) FROM TrackComment tc WHERE tc.track.id = :trackId " +
           "AND tc.status = :status AND tc.isDeleted = false")
    Long countByTrackIdAndStatus(@Param("trackId") Long trackId, 
                                  @Param("status") CommentStatus status);

    /**
     * Đếm tổng số comment (không bị xóa) của một track
     * @deprecated Sử dụng countByTrackIdInternal để chỉ đếm Internal Room comments
     */
    @Query("SELECT COUNT(tc) FROM TrackComment tc WHERE tc.track.id = :trackId " +
           "AND tc.isDeleted = false")
    Long countByTrackId(@Param("trackId") Long trackId);

    /**
     * Đếm số lượng comment của một track trong Internal Room theo status
     * Chỉ đếm comment không có clientDelivery (Internal Room)
     */
    @Query("SELECT COUNT(tc) FROM TrackComment tc WHERE tc.track.id = :trackId " +
           "AND tc.clientDelivery IS NULL " +
           "AND tc.status = :status AND tc.isDeleted = false")
    Long countByTrackIdAndStatusInternal(@Param("trackId") Long trackId, 
                                         @Param("status") CommentStatus status);

    /**
     * Đếm tổng số comment trong Internal Room (không bị xóa) của một track
     * Chỉ đếm comment không có clientDelivery (Internal Room)
     */
    @Query("SELECT COUNT(tc) FROM TrackComment tc WHERE tc.track.id = :trackId " +
           "AND tc.clientDelivery IS NULL " +
           "AND tc.isDeleted = false")
    Long countByTrackIdInternal(@Param("trackId") Long trackId);

    /**
     * Lấy tất cả comment gốc tại một timestamp cụ thể (trong Internal Room)
     * Chỉ lấy comment không có clientDelivery
     */
    @Query("SELECT tc FROM TrackComment tc WHERE tc.track.id = :trackId " +
           "AND tc.clientDelivery IS NULL " +
           "AND tc.timestamp = :timestamp AND tc.parentComment IS NULL " +
           "AND tc.isDeleted = false ORDER BY tc.createdAt ASC")
    List<TrackComment> findByTrackIdAndTimestamp(@Param("trackId") Long trackId, 
                                                  @Param("timestamp") Integer timestamp);

    // ==================== Client Room Comments ====================

    /**
     * Lấy tất cả comment gốc trong Client Room (có clientDelivery)
     * Sắp xếp theo timestamp tăng dần
     */
    @Query("SELECT tc FROM TrackComment tc WHERE tc.clientDelivery.id = :clientDeliveryId " +
           "AND tc.parentComment IS NULL AND tc.isDeleted = false " +
           "ORDER BY tc.timestamp ASC, tc.createdAt ASC")
    Page<TrackComment> findRootCommentsByClientDeliveryId(@Param("clientDeliveryId") Long clientDeliveryId, Pageable pageable);

    /**
     * Lấy tất cả reply của một comment trong Client Room
     */
    @Query("SELECT tc FROM TrackComment tc WHERE tc.parentComment.id = :parentCommentId " +
           "AND tc.clientDelivery IS NOT NULL " +
           "AND tc.isDeleted = false ORDER BY tc.createdAt ASC")
    List<TrackComment> findClientRoomRepliesByParentCommentId(@Param("parentCommentId") Long parentCommentId);

    /**
     * Đếm số lượng reply của một comment trong Client Room
     */
    @Query("SELECT COUNT(tc) FROM TrackComment tc WHERE tc.parentComment.id = :parentCommentId " +
           "AND tc.clientDelivery IS NOT NULL AND tc.isDeleted = false")
    Long countClientRoomRepliesByParentCommentId(@Param("parentCommentId") Long parentCommentId);

    /**
     * Lấy comment tại một timestamp cụ thể trong Client Room
     */
    @Query("SELECT tc FROM TrackComment tc WHERE tc.clientDelivery.id = :clientDeliveryId " +
           "AND tc.timestamp = :timestamp AND tc.parentComment IS NULL " +
           "AND tc.isDeleted = false ORDER BY tc.createdAt ASC")
    List<TrackComment> findByClientDeliveryIdAndTimestamp(@Param("clientDeliveryId") Long clientDeliveryId, 
                                                           @Param("timestamp") Integer timestamp);

    /**
     * Đếm tổng số comment trong Client Room
     */
    @Query("SELECT COUNT(tc) FROM TrackComment tc WHERE tc.clientDelivery.id = :clientDeliveryId " +
           "AND tc.isDeleted = false")
    Long countByClientDeliveryId(@Param("clientDeliveryId") Long clientDeliveryId);

    /**
     * Đếm số lượng comment trong Client Room theo status
     */
    @Query("SELECT COUNT(tc) FROM TrackComment tc WHERE tc.clientDelivery.id = :clientDeliveryId " +
           "AND tc.status = :status AND tc.isDeleted = false")
    Long countByClientDeliveryIdAndStatus(@Param("clientDeliveryId") Long clientDeliveryId, 
                                          @Param("status") CommentStatus status);

    /**
     * Lấy comment gốc trong Internal Room (không có clientDelivery)
     * Sắp xếp theo timestamp tăng dần
     */
    @Query("SELECT tc FROM TrackComment tc WHERE tc.track.id = :trackId " +
           "AND tc.clientDelivery IS NULL " +
           "AND tc.parentComment IS NULL AND tc.isDeleted = false " +
           "ORDER BY tc.timestamp ASC, tc.createdAt ASC")
    Page<TrackComment> findRootCommentsByTrackIdInternal(@Param("trackId") Long trackId, Pageable pageable);
}



