package com.fpt.producerworkbench.service;


import io.agora.media.RtcTokenBuilder.Role;

public interface AgoraTokenService {

    String generateToken(String channelName);
    String generateRtcToken(String channelName, int uid, Role role, int expirationTimeInSeconds);
}
