package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.TrackStatus;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.entity.TrackStatusTransitionLog;
import com.fpt.producerworkbench.entity.User;

import java.util.List;
import java.util.Map;

/**
 * Service quản lý track status transitions và audit trail
 */
public interface TrackStatusTransitionService {

    /**
     * Log một transition của track status
     *
     * @param track Track được transition
     * @param fromStatus Status cũ
     * @param toStatus Status mới
     * @param triggeredBy User trigger transition
     * @param reason Lý do transition (có thể null)
     * @param metadata Metadata bổ sung (có thể null)
     */
    void logTransition(Track track, String fromStatus, String toStatus, 
                      User triggeredBy, String reason, Map<String, Object> metadata);

    /**
     * Lấy lịch sử transitions của track
     *
     * @param trackId ID của track
     * @return Danh sách transitions sắp xếp theo thời gian giảm dần
     */
    List<TrackStatusTransitionLog> getTrackHistory(Long trackId);

    /**
     * Validate xem transition từ status này sang status kia có hợp lệ không
     * Theo FSM rules
     *
     * @param from Status hiện tại
     * @param to Status muốn chuyển sang
     * @throws com.fpt.producerworkbench.exception.AppException nếu transition không hợp lệ
     */
    void validateTransition(TrackStatus from, TrackStatus to);
}

