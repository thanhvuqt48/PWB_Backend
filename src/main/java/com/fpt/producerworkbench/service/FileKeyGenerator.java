package com.fpt.producerworkbench.service;

public interface FileKeyGenerator {

    String generateUserAvatarKey(Long userId, String originalFilename);

    String generateProjectFileKey(Long projectId, String originalFilename);

    String generateMilestoneDeliveryKey(Long projectId, Long milestoneId, String originalFilename);

    String generateContractDocumentKey(Long contractId, String fileName);

    String generatePortfolioCoverImageKey(Long userId, String originalFilename);

    String generatePersonalProjectImageKey(Long userId, Long personalProjectId, String originalFilename);

    String generatePersonalProjectAudioDemoKey(Long userId, Long personalProjectId, String originalFilename);

    String generateInspirationAssetKey(Long projectId, String originalFilename);

    String generateInspirationAudioKey(Long projectId, String fileName);

    String generateChatMessageFileKey(String conversationId, String originalFilename);

    String generateConversationAvatarKey(String conversationId, String originalFilename);

    String generateMilestoneConversationAvatarKey(Long milestoneId, String originalFilename);

    /**
     * Sinh key cho file master audio của track
     *
     * @param trackId          ID của track
     * @param originalFilename Tên file gốc
     * @return S3 key cho file master
     */
    String generateTrackMasterKey(Long trackId, String originalFilename);

    /**
     * Sinh prefix cho thư mục HLS của track
     *
     * @param trackId ID của track
     * @return S3 prefix cho thư mục HLS
     */
    String generateTrackHlsPrefix(Long trackId);

    /**
     * Sinh key cho file voice tag audio của track
     *
     * @param trackId ID của track
     * @return S3 key cho file voice tag
     */
    String generateTrackVoiceTagKey(Long trackId);

    /**
     * Sinh key cho file CCCD của user
     *
     * @param userId ID của user
     * @param side Mặt CCCD: "front" hoặc "back"
     * @param originalFilename Tên file gốc
     * @return S3 key cho file CCCD
     */
    String generateCccdKey(Long userId, String side, String originalFilename);
}