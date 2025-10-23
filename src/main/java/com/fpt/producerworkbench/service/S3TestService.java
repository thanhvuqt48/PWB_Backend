package com.fpt.producerworkbench.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Interface cho dịch vụ test các chức năng S3.
 */
public interface S3TestService {

    String uploadUserAvatar(Long userId, MultipartFile file);
    String uploadProjectMusic(Long projectId, MultipartFile file);
    String uploadProjectVideo(Long projectId, MultipartFile file);
    String uploadProjectImage(Long projectId, MultipartFile file);
    String uploadMilestoneZip(Long projectId, Long milestoneId, MultipartFile file);
    String uploadContractPdf(Long contractId, String fileName, MultipartFile file);

    List<String> uploadProjectFiles(Long projectId, List<MultipartFile> files);

    String getViewableUrl(String objectKey);

    String getDownloadUrl(String objectKey, String originalFileName);
    void deleteFile(String objectKey);


}