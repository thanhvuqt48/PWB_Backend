package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.SendTrackToClientRequest;
import com.fpt.producerworkbench.dto.request.UpdateClientDeliveryStatusRequest;
import com.fpt.producerworkbench.dto.response.ClientDeliveryResponse;
import com.fpt.producerworkbench.dto.response.ClientTrackResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Service quản lý việc gửi track từ phòng nội bộ cho khách hàng
 */
public interface ClientDeliveryService {

    ClientDeliveryResponse sendTrackToClient(Authentication auth, Long trackId, SendTrackToClientRequest request);

    List<ClientTrackResponse> getClientTracks(Authentication auth, Long milestoneId);

    ClientTrackResponse getClientTrackByDeliveryId(Authentication auth, Long deliveryId);

    ClientDeliveryResponse updateDeliveryStatus(Authentication auth, Long deliveryId, UpdateClientDeliveryStatusRequest request);

    Integer getProductCountRemaining(Long milestoneId);

    Integer getEditCountRemaining(Long milestoneId);

    void cancelDelivery(Authentication auth, Long deliveryId);
}

