package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.TicketStatus;
import com.fpt.producerworkbench.dto.request.TicketReplyRequest;
import com.fpt.producerworkbench.dto.response.TicketReplyResponse;
import com.fpt.producerworkbench.dto.request.TicketRequest;
import com.fpt.producerworkbench.dto.response.TicketResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketReplyRepository ticketReplyRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final FileStorageService storage;

    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> IMAGE_MIMES = Set.of(
            MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp", "image/gif"
    );

    @Override
    @Transactional
    public TicketResponse createTicket(TicketRequest request, List<MultipartFile> files, String currentEmail) {
        User user = getUserOrThrow(currentEmail);

        String role = user.getRole().name();
        if ("ADMIN".equals(role)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Admin không được tạo ticket");
        }

        Project project = null;
        if (request.getProjectId() != null) {
            project = projectRepository.findById(request.getProjectId())
                    .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));
        }

        List<String> s3Keys = uploadAttachments(files, "tickets");

        Ticket ticket = Ticket.builder()
                .user(user)
                .project(project)
                .title(request.getTitle())
                .status(TicketStatus.OPEN)
                .attachmentKeys(s3Keys)
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);

        TicketReply firstReply = TicketReply.builder()
                .ticket(savedTicket)
                .user(user)
                .content(request.getContent())
                .build();
        ticketReplyRepository.save(firstReply);

        return mapToResponse(savedTicket);
    }

    @Override
    public Page<TicketResponse> getMyTickets(Pageable pageable, String currentEmail) {
        User user = getUserOrThrow(currentEmail);
        Page<Ticket> ticketPage = ticketRepository.findByUserId(user.getId(), pageable);
        return ticketPage.map(this::mapToResponse);
    }

    @Override
    public Page<TicketResponse> getAllTickets(Pageable pageable, String currentEmail) {
        User user = getUserOrThrow(currentEmail);

        if (!"ADMIN".equals(user.getRole().name())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        return ticketRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional
    public TicketReplyResponse createReply(Long ticketId, TicketReplyRequest request, List<MultipartFile> files, String currentEmail) {
        User currentUser = getUserOrThrow(currentEmail);
        Ticket ticket = getTicketOrThrow(ticketId);

        boolean isAdmin = "ADMIN".equals(currentUser.getRole().name());
        boolean isOwner = ticket.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        List<String> s3Keys = uploadAttachments(files, "ticket-replies");

        TicketReply reply = TicketReply.builder()
                .ticket(ticket)
                .user(currentUser)
                .content(request.getContent())
                .attachmentKeys(s3Keys)
                .build();

        TicketReply savedReply = ticketReplyRepository.save(reply);

        if (isAdmin && ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
        }

        return mapToReplyResponse(savedReply, currentUser.getRole().name());
    }

    @Override
    public List<TicketReplyResponse> getTicketReplies(Long ticketId, String currentEmail) {
        User currentUser = getUserOrThrow(currentEmail);
        Ticket ticket = getTicketOrThrow(ticketId);

        boolean isAdmin = "ADMIN".equals(currentUser.getRole().name());
        boolean isOwner = ticket.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        List<TicketReply> replies = ticketReplyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

        return replies.stream()
                .map(reply -> mapToReplyResponse(reply, reply.getUser().getRole().name()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TicketResponse updateTicketStatus(Long ticketId, TicketStatus newStatus, String currentEmail) {
        User currentUser = getUserOrThrow(currentEmail);
        Ticket ticket = getTicketOrThrow(ticketId);

        if (!"ADMIN".equals(currentUser.getRole().name())) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Chỉ Admin mới có quyền cập nhật trạng thái");
        }

        ticket.setStatus(newStatus);
        Ticket savedTicket = ticketRepository.save(ticket);

        return mapToResponse(savedTicket);
    }


    private User getUserOrThrow(String email) {
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private Ticket getTicketOrThrow(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND)); // Hoặc TICKET_NOT_FOUND
    }

    private List<String> uploadAttachments(List<MultipartFile> files, String folderPrefix) {
        List<String> uploadedKeys = new ArrayList<>();
        if (files == null || files.isEmpty()) return uploadedKeys;

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            if (file.getSize() > MAX_SIZE) {
                throw new AppException(ErrorCode.FILE_TOO_LARGE);
            }

            String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            if (!IMAGE_MIMES.contains(mime)) {
                throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }

            String key = folderPrefix + "/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
            storage.uploadFile(file, key);
            uploadedKeys.add(key);
        }
        return uploadedKeys;
    }

    private List<String> getPresignedUrls(List<String> keys) {
        if (keys == null || keys.isEmpty()) return new ArrayList<>();
        return keys.stream()
                .map(key -> storage.generatePresignedUrl(key, false, null))
                .collect(Collectors.toList());
    }

    private TicketResponse mapToResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .status(ticket.getStatus().name())
                .createdBy(ticket.getUser().getFullName())
                .projectName(ticket.getProject() != null ? ticket.getProject().getTitle() : "N/A")
                .createdAt(convertDateToLocalDateTime(ticket.getCreatedAt()))
                .attachmentUrls(getPresignedUrls(ticket.getAttachmentKeys()))
                .build();
    }

    private TicketReplyResponse mapToReplyResponse(TicketReply reply, String role) {
        return TicketReplyResponse.builder()
                .id(reply.getId())
                .ticketId(reply.getTicket().getId())
                .content(reply.getContent())
                .senderName(reply.getUser().getFullName())
                .senderRole(role)
                .createdAt(convertDateToLocalDateTime(reply.getCreatedAt()))
                .attachmentUrls(getPresignedUrls(reply.getAttachmentKeys()))
                .build();
    }

    private java.time.LocalDateTime convertDateToLocalDateTime(java.util.Date dateToConvert) {
        if (dateToConvert == null) return null;
        return dateToConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}