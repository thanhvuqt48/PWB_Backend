package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.common.TicketStatus;
import com.fpt.producerworkbench.dto.request.TicketReplyRequest;
import com.fpt.producerworkbench.dto.response.TicketReplyResponse;
import com.fpt.producerworkbench.dto.request.TicketRequest;
import com.fpt.producerworkbench.dto.response.TicketResponse;
import com.fpt.producerworkbench.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketResponse> createTicket(
            @RequestPart("data") TicketRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Principal principal
    ) {
        return ResponseEntity.ok(ticketService.createTicket(request, files, principal.getName()));
    }

    @GetMapping
    public ResponseEntity<Page<TicketResponse>> getMyTickets(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal
    ) {
        return ResponseEntity.ok(ticketService.getMyTickets(pageable, principal.getName()));
    }

    @GetMapping("/admin")
    public ResponseEntity<Page<TicketResponse>> getAllTickets(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal
    ) {
        return ResponseEntity.ok(ticketService.getAllTickets(pageable, principal.getName()));
    }

    @PostMapping(value = "/{id}/replies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketReplyResponse> createReply(
            @PathVariable("id") Long ticketId,
            @RequestPart("data") TicketReplyRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Principal principal
    ) {
        return ResponseEntity.ok(ticketService.createReply(ticketId, request, files, principal.getName()));
    }

    @GetMapping("/{id}/replies")
    public ResponseEntity<List<TicketReplyResponse>> getTicketReplies(
            @PathVariable("id") Long ticketId,
            Principal principal
    ) {
        return ResponseEntity.ok(ticketService.getTicketReplies(ticketId, principal.getName()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateTicketStatus(
            @PathVariable("id") Long ticketId,
            @RequestParam TicketStatus status,
            Principal principal
    ) {
        return ResponseEntity.ok(ticketService.updateTicketStatus(ticketId, status, principal.getName()));
    }
}