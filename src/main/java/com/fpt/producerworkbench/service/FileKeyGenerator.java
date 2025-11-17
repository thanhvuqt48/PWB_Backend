package com.fpt.producerworkbench.service;

public interface FileKeyGenerator {

    String generateUserAvatarKey(Long userId, String originalFilename);

    String generateProjectFileKey(Long projectId, String originalFilename);

    String generateMilestoneDeliveryKey(Long projectId, Long milestoneId, String originalFilename);

    String generateContractDocumentKey(Long contractId, String fileName);

    String generatePortfolioCoverImageKey(Long userId, String originalFilename);

    String generatePersonalProjectImageKey(Long userId, Long personalProjectId, String originalFilename);

    String generateInspirationAssetKey(Long projectId, String originalFilename);

    String generateInspirationAudioKey(Long projectId, String fileName);
}