package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.TrackStatus;
import com.fpt.producerworkbench.entity.Track;
import com.fpt.producerworkbench.entity.TrackStatusTransitionLog;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.TrackStatusTransitionLogRepository;
import com.fpt.producerworkbench.service.TrackStatusTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Implementation của TrackStatusTransitionService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackStatusTransitionServiceImpl implements TrackStatusTransitionService {

    private final TrackStatusTransitionLogRepository transitionLogRepository;

    /**
     * FSM cho Track Status transitions
     * Key: Status hiện tại
     * Value: Set các status có thể chuyển sang
     */
    private static final Map<TrackStatus, Set<TrackStatus>> ALLOWED_TRANSITIONS = Map.of(
        TrackStatus.INTERNAL_DRAFT, Set.of(
            TrackStatus.INTERNAL_REJECTED, 
            TrackStatus.INTERNAL_APPROVED
        ),
        TrackStatus.INTERNAL_REJECTED, Set.of(
            TrackStatus.INTERNAL_DRAFT,
            TrackStatus.INTERNAL_APPROVED
        ),
        TrackStatus.INTERNAL_APPROVED, Set.of(
            TrackStatus.INTERNAL_REJECTED
        )
    );

    @Override
    @Transactional
    public void logTransition(Track track, String fromStatus, String toStatus,
                             User triggeredBy, String reason, Map<String, Object> metadata) {
        log.info("Logging track status transition: trackId={}, from={}, to={}, triggeredBy={}",
                track.getId(), fromStatus, toStatus, triggeredBy.getId());

        TrackStatusTransitionLog log = TrackStatusTransitionLog.builder()
                .track(track)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .triggeredBy(triggeredBy)
                .reason(reason)
                .metadata(metadata)
                .build();

        transitionLogRepository.save(log);
    }

    @Override
    public List<TrackStatusTransitionLog> getTrackHistory(Long trackId) {
        return transitionLogRepository.findByTrackIdOrderByCreatedAtDesc(trackId);
    }

    @Override
    public void validateTransition(TrackStatus from, TrackStatus to) {
        if (from == to) {
            // Same status, no validation needed
            return;
        }

        Set<TrackStatus> allowedToStatuses = ALLOWED_TRANSITIONS.get(from);
        if (allowedToStatuses == null || !allowedToStatuses.contains(to)) {
            log.warn("Invalid track status transition: from={}, to={}", from, to);
            throw new AppException(ErrorCode.INVALID_DELIVERY_STATUS_TRANSITION);
        }
    }
}

