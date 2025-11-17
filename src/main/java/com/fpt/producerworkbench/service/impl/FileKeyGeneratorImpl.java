package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.FileKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class FileKeyGeneratorImpl implements FileKeyGenerator {

    @Override
    public String generateUserAvatarKey(Long userId, String originalFilename) {
        return String.format("users/%d/public/avatar/profile%s", userId, getFileExtension(originalFilename));
    }

    @Override
    public String generateProjectFileKey(Long projectId, String originalFilename) {
        String uuid = UUID.randomUUID().toString();
        return String.format("projects/%d/files/%s%s", projectId, uuid, getFileExtension(originalFilename));
    }

    @Override
    public String generateMilestoneDeliveryKey(Long projectId, Long milestoneId, String originalFilename) {
        String uuid = UUID.randomUUID().toString();
        return String.format("projects/%d/milestones/%d/%s%s", projectId, milestoneId, uuid, getFileExtension(originalFilename));
    }

    @Override
    public String generateContractDocumentKey(Long contractId, String fileName) {
        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Tên file không hợp lệ.");
        }
        if (!fileName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("File hợp đồng phải là định dạng PDF.");
        }
        return String.format("contracts/%d/%s", contractId, fileName);
    }

    @Override
    public String generatePortfolioCoverImageKey(Long userId, String originalFilename) {
        String uuid = UUID.randomUUID().toString();
        return String.format("users/%d/public/portfolio/cover/%s%s", userId, uuid, getFileExtension(originalFilename));
    }

    @Override
    public String generatePersonalProjectImageKey(Long userId, Long personalProjectId, String originalFilename) {
        String uuid = UUID.randomUUID().toString();
        return String.format("users/%d/public/portfolio/projects/%d/%s%s", userId, personalProjectId, uuid, getFileExtension(originalFilename));
    }

    @Override
    public String generateInspirationAssetKey(Long projectId, String originalFilename) {
        String uuid = UUID.randomUUID().toString();
        return String.format("projects/%d/inspiration/%s%s",
                projectId, uuid, getFileExtension(originalFilename));
    }

    @Override
    public String generateInspirationAudioKey(Long projectId, String fileName) {
        return "projects/" + projectId + "/Inspiration/" + UUID.randomUUID() + extOf(fileName);
    }

    private String extOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return (dot >= 0 ? name.substring(dot) : "");
    }

    private String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}