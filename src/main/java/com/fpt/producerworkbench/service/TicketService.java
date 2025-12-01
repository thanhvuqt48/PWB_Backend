package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.TicketStatus;
import com.fpt.producerworkbench.dto.request.TicketReplyRequest;
import com.fpt.producerworkbench.dto.response.TicketReplyResponse;
import com.fpt.producerworkbench.dto.request.TicketRequest;
import com.fpt.producerworkbench.dto.response.TicketResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TicketService {

    TicketResponse createTicket(TicketRequest request, List<MultipartFile> files, String currentEmail);

    Page<TicketResponse> getMyTickets(Pageable pageable, String currentEmail);

    Page<TicketResponse> getAllTickets(Pageable pageable, String currentEmail);

    TicketReplyResponse createReply(Long ticketId, TicketReplyRequest request, List<MultipartFile> files, String currentEmail);

    List<TicketReplyResponse> getTicketReplies(Long ticketId, String currentEmail);

    TicketResponse updateTicketStatus(Long ticketId, TicketStatus newStatus, String currentEmail);

    TicketResponse getTicketDetail(Long ticketId, String currentEmail);
}