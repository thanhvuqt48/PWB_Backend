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


@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClientDeliveryController {

    private final ClientDeliveryService clientDeliveryService;

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

}

