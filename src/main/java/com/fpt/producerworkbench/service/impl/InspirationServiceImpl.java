package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.InspirationType;
import com.fpt.producerworkbench.dto.request.InspirationNoteCreateRequest;
import com.fpt.producerworkbench.dto.response.InspirationItemResponse;
import com.fpt.producerworkbench.dto.response.InspirationListResponse;
import com.fpt.producerworkbench.entity.InspirationItem;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.InspirationItemRepository;
import com.fpt.producerworkbench.repository.ProjectMemberRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.InspirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InspirationServiceImpl implements InspirationService {

    private static final long MAX_SIZE = 50L * 1024 * 1024; // 50MB

    private static final Set<String> IMAGE_MIMES = Set.of(
            MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp", "image/gif"
    );
    private static final Set<String> VIDEO_MIMES = Set.of(
            "video/mp4", "video/quicktime", "video/webm", "video/x-matroska"
    );
    private static final Set<String> AUDIO_MIMES = Set.of(
            "audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav", "audio/webm", "audio/ogg"
    );

    private final InspirationItemRepository inspirationRepo;
    private final ProjectRepository projectRepo;
    private final UserRepository userRepo;
    private final ProjectMemberRepository projectMemberRepo;
    private final FileKeyGenerator keyGen;
    private final FileStorageService storage;


    @Override
    public InspirationItemResponse uploadAsset(Long projectId,
                                               Long uploaderId,
                                               InspirationType type,
                                               MultipartFile file) {
        Project project = requireProject(projectId);
        User uploader = requireUser(uploaderId);
        ensureMember(project, uploader); // chỉ thành viên mới được dùng

        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        validateMime(type, mime);

        String key = keyGen.generateInspirationAssetKey(projectId, file.getOriginalFilename());
        storage.uploadFile(file, key);

        InspirationItem saved = inspirationRepo.save(InspirationItem.builder()
                .project(project)
                .uploader(uploader)
                .type(type)
                .title(file.getOriginalFilename())
                .s3Key(key)
                .mimeType(mime)
                .sizeBytes(file.getSize())
                .build());

        return toResponse(saved, true);
    }

    @Override
    public InspirationItemResponse createNote(Long projectId,
                                              Long uploaderId,
                                              InspirationNoteCreateRequest request) {
        Project project = requireProject(projectId);
        User uploader = requireUser(uploaderId);
        ensureMember(project, uploader);

        InspirationItem saved = inspirationRepo.save(InspirationItem.builder()
                .project(project)
                .uploader(uploader)
                .type(InspirationType.NOTE)
                .title(request.getTitle())
                .noteContent(request.getContent())
                .build());

        return toResponse(saved, false);
    }

    @Override
    public InspirationListResponse<InspirationItemResponse> list(Long projectId,
                                                                 InspirationType type,
                                                                 Pageable pageable,
                                                                 Long requesterId) {
        Project project = requireProject(projectId);
        User requester = requireUser(requesterId);
        ensureMember(project, requester);

        Page<InspirationItem> page = (type == null)
                ? inspirationRepo.findByProject_IdOrderByCreatedAtDesc(projectId, pageable)
                : inspirationRepo.findByProject_IdAndTypeOrderByCreatedAtDesc(projectId, type, pageable);

        return InspirationListResponse.<InspirationItemResponse>builder()
                .items(page.getContent().stream().map(i -> toResponse(i, false)).collect(Collectors.toList()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    @Override
    public void delete(Long projectId, Long itemId, Long requesterId) {
        Project project = requireProject(projectId);
        User requester = requireUser(requesterId);
        ensureMember(project, requester);

        InspirationItem item = inspirationRepo.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.INSPIRATION_ITEM_NOT_FOUND));

        if (!item.getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.NOT_FOUND);
        }

        // Quyền xóa: người upload hoặc chủ dự án (bạn có thể mở rộng theo ProjectRole nếu muốn)
        boolean canDelete = item.getUploader().getId().equals(requesterId)
                || (project.getCreator() != null && requesterId.equals(project.getCreator().getId()));

        if (!canDelete) throw new AppException(ErrorCode.ACCESS_DENIED);

        if (item.getS3Key() != null) {
            try {
                storage.deleteFile(item.getS3Key());
            } catch (Exception ex) {
                log.warn("[Inspiration] Delete S3 '{}' failed: {}", item.getS3Key(), ex.getMessage());
            }
        }
        inspirationRepo.delete(item);
    }

    @Override
    public String getViewUrl(Long projectId, Long itemId, Long requesterId) {
        Project project = requireProject(projectId);
        User requester = requireUser(requesterId);
        ensureMember(project, requester);

        InspirationItem item = requireItem(projectId, itemId);
        if (item.getS3Key() == null) throw new AppException(ErrorCode.INVALID_REQUEST);

        return storage.generatePresignedUrl(item.getS3Key(), false, null);
    }

    @Override
    public String getDownloadUrl(Long projectId,
                                 Long itemId,
                                 String originalFileName,
                                 Long requesterId) {
        Project project = requireProject(projectId);
        User requester = requireUser(requesterId);
        ensureMember(project, requester);

        InspirationItem item = requireItem(projectId, itemId);
        if (item.getS3Key() == null) throw new AppException(ErrorCode.INVALID_REQUEST);

        String fileName = (originalFileName == null || originalFileName.isBlank())
                ? (item.getTitle() == null ? "download" : item.getTitle())
                : originalFileName;

        return storage.generatePresignedUrl(item.getS3Key(), true, fileName);
    }


    private void validateMime(InspirationType type, String mime) {
        boolean ok = switch (type) {
            case IMAGE -> IMAGE_MIMES.contains(mime);
            case VIDEO -> VIDEO_MIMES.contains(mime);
            case AUDIO -> AUDIO_MIMES.contains(mime);
            default -> false; // NOTE không đi qua uploadAsset
        };
        if (!ok) throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    private Project requireProject(Long id) {
        return projectRepo.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private User requireUser(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private InspirationItem requireItem(Long projectId, Long itemId) {
        InspirationItem item = inspirationRepo.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.INSPIRATION_ITEM_NOT_FOUND));
        if (!item.getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.NOT_FOUND);
        }
        return item;
    }

    private void ensureMember(Project project, User user) {
        boolean isMember =
                projectMemberRepo.existsByProject_IdAndUser_Id(project.getId(), user.getId())
                        || (project.getCreator() != null && user.getId().equals(project.getCreator().getId()))
                        || (project.getClient() != null && user.getId().equals(project.getClient().getId()));

        if (!isMember) {
            throw new AppException(ErrorCode.NOT_PROJECT_MEMBER);
        }
    }

    private InspirationItemResponse toResponse(InspirationItem i, boolean includeViewUrl) {
        String view = (includeViewUrl && i.getS3Key() != null)
                ? storage.generatePresignedUrl(i.getS3Key(), false, null)
                : null;

        String uploaderName =
                i.getUploader().getFullName() != null && !i.getUploader().getFullName().isBlank()
                        ? i.getUploader().getFullName()
                        : (i.getUploader().getEmail() == null ? "Unknown" : i.getUploader().getEmail());

        return InspirationItemResponse.builder()
                .id(i.getId())
                .type(i.getType())
                .title(i.getTitle())
                .noteContent(i.getNoteContent())
                .fileKey(i.getS3Key())
                .viewUrl(view)
                .mimeType(i.getMimeType())
                .sizeBytes(i.getSizeBytes())
                .uploaderId(i.getUploader().getId())
                .uploaderName(uploaderName)
                .build();
    }
}