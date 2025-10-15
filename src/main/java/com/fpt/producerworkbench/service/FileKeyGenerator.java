package com.fpt.producerworkbench.service;

public interface FileKeyGenerator {

    String generateUserAvatarKey(Long userId, String originalFilename);

    String generateProjectFileKey(Long projectId, String originalFilename);

    String generateMilestoneDeliveryKey(Long projectId, Long milestoneId, String originalFilename);

    String generateContractDocumentKey(Long contractId, String fileName);
}