package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.configuration.AgoraConfig;
import com.fpt.producerworkbench.exception.AgoraTokenException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.AgoraTokenService;
import io.agora.media.RtcTokenBuilder2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgoraTokenServiceImpl implements AgoraTokenService {

    private final AgoraConfig agoraConfig;

    @Override
    public String generateRtcToken(
            String channelName,
            int uid,
            RtcTokenBuilder2.Role role,
            int expirationSeconds) {

        // Validate inputs
        validateChannel(channelName);
        validateUid(uid);
        validateConfig();

        try {
            int privilegeExpireTime = (int) (System.currentTimeMillis() / 1000) + expirationSeconds;

            log.debug("Generating token - Channel: {}, UID: {}, Role: {}", channelName, uid, role);

            // ✅ Create instance and call instance method
            RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
            String token = tokenBuilder.buildTokenWithUid(
                    agoraConfig.getAppId(),
                    agoraConfig.getAppCertificate(),
                    channelName,
                    uid,
                    role,
                    privilegeExpireTime,
                    privilegeExpireTime
            );

            log.info("✅ Token generated successfully for channel: {}", channelName);
            return token;

        } catch (Exception e) {
            log.error("❌ Failed to generate Agora token: {}", e.getMessage(), e);
            throw new AgoraTokenException(ErrorCode.AGORA_TOKEN_GENERATION_FAILED, e);
        }
    }

    // ========== Validation Methods ==========

    private void validateChannel(String channelName) {
        if (channelName == null || channelName.trim().isEmpty()) {
            throw new AgoraTokenException(
                    ErrorCode.AGORA_CHANNEL_INVALID,
                    "Channel name cannot be empty"
            );
        }

        if (channelName.length() > 64) {
            throw new AgoraTokenException(
                    ErrorCode.AGORA_CHANNEL_INVALID,
                    "Channel name cannot exceed 64 characters"
            );
        }
    }

    private void validateUid(int uid) {
        if (uid < 0) {
            throw new AgoraTokenException(
                    ErrorCode.AGORA_UID_INVALID,
                    "UID cannot be negative"
            );
        }
    }

    private void validateConfig() {
        String appId = agoraConfig.getAppId();
        String appCert = agoraConfig.getAppCertificate();

        if (appId == null || appId.trim().isEmpty()) {
            throw new AgoraTokenException(
                    ErrorCode.AGORA_CONFIG_MISSING,
                    "Agora App ID is not configured"
            );
        }

        if (appCert == null || appCert.trim().isEmpty()) {
            throw new AgoraTokenException(
                    ErrorCode.AGORA_CONFIG_MISSING,
                    "Agora App Certificate is not configured"
            );
        }
    }
}
