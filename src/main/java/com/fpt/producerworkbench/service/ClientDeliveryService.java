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

    /**
     * Gửi track cho client
     * Chỉ Owner được gửi
     *
     * @param auth Authentication
     * @param trackId ID của track
     * @param request Thông tin gửi track
     * @return Thông tin delivery đã tạo
     */
    ClientDeliveryResponse sendTrackToClient(Authentication auth, Long trackId, SendTrackToClientRequest request);

    /**
     * Lấy danh sách tracks đã gửi cho client trong milestone
     * Permission: Owner, Admin, Client, Observer (nếu project.isFunded = true)
     *
     * @param auth Authentication
     * @param milestoneId ID của milestone
     * @return Danh sách tracks
     */
    List<ClientTrackResponse> getClientTracks(Authentication auth, Long milestoneId);

    /**
     * Lấy chi tiết track trong Client Room theo deliveryId
     * Permission: Owner, Admin, Client, Observer (nếu project.isFunded = true)
     *
     * @param auth Authentication
     * @param deliveryId ID của ClientDelivery
     * @return Thông tin track và delivery
     */
    ClientTrackResponse getClientTrackByDeliveryId(Authentication auth, Long deliveryId);

    /**
     * Cập nhật status của delivery (client từ chối hoặc yêu cầu chỉnh sửa)
     * Permission: Client, Observer, Owner
     *
     * @param auth Authentication
     * @param deliveryId ID của delivery
     * @param request Thông tin cập nhật
     * @return Thông tin delivery đã cập nhật
     */
    ClientDeliveryResponse updateDeliveryStatus(Authentication auth, Long deliveryId, UpdateClientDeliveryStatusRequest request);

    /**
     * Lấy số lượng product_count còn lại của milestone
     *
     * @param milestoneId ID của milestone
     * @return Số lượng còn lại
     */
    Integer getProductCountRemaining(Long milestoneId);

    /**
     * Lấy số lượng edit_count còn lại của milestone
     *
     * @param milestoneId ID của milestone
     * @return Số lượng còn lại
     */
    Integer getEditCountRemaining(Long milestoneId);

    /**
     * Hủy delivery (optional - để rollback)
     *
     * @param auth Authentication
     * @param deliveryId ID của delivery
     */
    void cancelDelivery(Authentication auth, Long deliveryId);
}

