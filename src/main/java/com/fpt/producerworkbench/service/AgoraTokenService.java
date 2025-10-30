package com.fpt.producerworkbench.service;

import io.agora.media.RtcTokenBuilder2;

public interface AgoraTokenService {

    /**
     * Generate Agora RTC token using RtcTokenBuilder2
     * @param channelName Channel name
     * @param uid User ID
     * @param role Publisher or Subscriber role
     * @param expirationSeconds Token expiration in seconds
     * @return Generated token
     */
    String generateRtcToken(
            String channelName,
            int uid,
            RtcTokenBuilder2.Role role,
            int expirationSeconds
    );
}
