package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.SendTrackToClientRequest;
import com.fpt.producerworkbench.dto.request.UpdateClientDeliveryStatusRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ClientDeliveryResponse;
import com.fpt.producerworkbench.dto.response.ClientTrackResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.ClientDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller cho quản lý việc gửi track từ phòng nội bộ cho khách hàng
 * 
 * Endpoints:
 * - POST /api/v1/tracks/{trackId}/send-to-client - Gửi track cho client (Owner only)
 * - GET /api/v1/milestones/{milestoneId}/client-tracks - Lấy danh sách tracks trong Client Room
 * - PUT /api/v1/client-deliveries/{deliveryId}/status - Cập nhật status delivery (Client/Observer/Owner)
 * - GET /api/v1/milestones/{milestoneId}/quota - Lấy số lượt gửi còn lại (Owner/Admin/Client/Observer)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClientDeliveryController {

    private final ClientDeliveryService clientDeliveryService;

    /**
     * Gửi track cho client
     * Permission: Chỉ Owner của project được gửi
     * 
     * @param trackId ID của track cần gửi
     * @param request Request chứa note (optional)
     * @param authentication Authentication
     * @return Thông tin delivery đã tạo
     */
    @PostMapping("/tracks/{trackId}/send-to-client")
    public ApiResponse<ClientDeliveryResponse> sendTrackToClient(
            @PathVariable Long trackId,
            @RequestBody(required = false) SendTrackToClientRequest request,
            Authentication authentication) {

        if (trackId == null || trackId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        if (request == null) {
            request = new SendTrackToClientRequest();
        }

        ClientDeliveryResponse response = clientDeliveryService.sendTrackToClient(authentication, trackId, request);

        return ApiResponse.<ClientDeliveryResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Đã gửi track cho khách hàng thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy danh sách tracks đã gửi cho client trong milestone (Client Room)
     * Permission: Owner, Admin, Client, Observer (nếu project.isFunded = true)
     * 
     * @param milestoneId ID của milestone
     * @param authentication Authentication
     * @return Danh sách tracks trong Client Room
     */
    @GetMapping("/milestones/{milestoneId}/client-tracks")
    public ApiResponse<List<ClientTrackResponse>> getClientTracks(
            @PathVariable Long milestoneId,
            Authentication authentication) {

        if (milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        List<ClientTrackResponse> tracks = clientDeliveryService.getClientTracks(authentication, milestoneId);

        return ApiResponse.<List<ClientTrackResponse>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy danh sách tracks trong Client Room thành công")
                .result(tracks)
                .build();
    }

    /**
     * Lấy chi tiết track trong Client Room theo deliveryId
     * Permission: Owner, Admin, Client, Observer (nếu project.isFunded = true)
     * 
     * @param deliveryId ID của ClientDelivery
     * @param authentication Authentication
     * @return Thông tin track và delivery
     */
    @GetMapping("/client-deliveries/{deliveryId}/track-detail")
    public ApiResponse<ClientTrackResponse> getClientTrackDetail(
            @PathVariable Long deliveryId,
            Authentication authentication) {

        if (deliveryId == null || deliveryId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        ClientTrackResponse track = clientDeliveryService.getClientTrackByDeliveryId(authentication, deliveryId);

        return ApiResponse.<ClientTrackResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy chi tiết track trong Client Room thành công")
                .result(track)
                .build();
    }

    /**
     * Cập nhật status của delivery (client từ chối, yêu cầu chỉnh sửa hoặc chấp nhận)
     * Permission: Client, Observer, Owner
     * 
     * @param deliveryId ID của delivery
     * @param request Request chứa status mới và reason
     * @param authentication Authentication
     * @return Thông tin delivery đã cập nhật
     */
    @PutMapping("/client-deliveries/{deliveryId}/status")
    public ApiResponse<ClientDeliveryResponse> updateDeliveryStatus(
            @PathVariable Long deliveryId,
            @Valid @RequestBody UpdateClientDeliveryStatusRequest request,
            Authentication authentication) {

        if (deliveryId == null || deliveryId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        ClientDeliveryResponse response = clientDeliveryService.updateDeliveryStatus(
                authentication, deliveryId, request
        );

        return ApiResponse.<ClientDeliveryResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Đã cập nhật trạng thái delivery thành công")
                .result(response)
                .build();
    }

    /**
     * Lấy số lượng product_count và edit_count còn lại của milestone
     * Permission: Owner, Admin, Client, Observer (nếu funded)
     * 
     * @param milestoneId ID của milestone
     * @param authentication Authentication
     * @return Quota information (product count và edit count)
     */
    @GetMapping("/milestones/{milestoneId}/quota")
    public ApiResponse<Map<String, Object>> getMilestoneQuota(
            @PathVariable Long milestoneId,
            Authentication authentication) {

        if (milestoneId == null || milestoneId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        Integer productCountRemaining = clientDeliveryService.getProductCountRemaining(milestoneId);
        Integer editCountRemaining = clientDeliveryService.getEditCountRemaining(milestoneId);
        
        return ApiResponse.<Map<String, Object>>builder()
                .code(HttpStatus.OK.value())
                .message("Lấy thông tin quota thành công")
                .result(Map.of(
                    "productCountRemaining", productCountRemaining,
                    "editCountRemaining", editCountRemaining
                ))
                .build();
    }

    /**
     * Hủy delivery (optional - để rollback)
     * Permission: Owner only
     * 
     * @param deliveryId ID của delivery
     * @param authentication Authentication
     * @return Success message
     */
    @DeleteMapping("/client-deliveries/{deliveryId}")
    public ApiResponse<Void> cancelDelivery(
            @PathVariable Long deliveryId,
            Authentication authentication) {

        if (deliveryId == null || deliveryId <= 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        clientDeliveryService.cancelDelivery(authentication, deliveryId);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Đã hủy delivery thành công")
                .build();
    }
}

