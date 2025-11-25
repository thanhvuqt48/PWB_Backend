package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ConversationCreationRequest;
import com.fpt.producerworkbench.dto.request.ConversationMemberAdditionRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ConversationCreationResponse;
import com.fpt.producerworkbench.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    ApiResponse<ConversationCreationResponse> createConversation(@RequestBody ConversationCreationRequest request) {
        return ApiResponse.<ConversationCreationResponse>builder()
                .code(HttpStatus.CREATED.value())
                .result(conversationService.create(request))
                .build();
    }

    @GetMapping
    ApiResponse<List<ConversationCreationResponse>> getAllMyConversation() {
        return ApiResponse.<List<ConversationCreationResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(conversationService.myConversation())
                .build();
    }

    @DeleteMapping("/{conversationId}")
    ApiResponse<Void> deleteConversation(@PathVariable String conversationId) {
        conversationService.deleteConversation(conversationId);
        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Conversation deleted")
                .build();
    }

    @PostMapping("/{conversationId}/members")
    ApiResponse<ConversationCreationResponse> addMembersToConversation(
            @PathVariable String conversationId,
            @RequestBody ConversationMemberAdditionRequest request
    ) {
        return ApiResponse.<ConversationCreationResponse>builder()
                .code(HttpStatus.OK.value())
                .result(conversationService.addMembersToGroup(conversationId, request.getMemberIds()))
                .build();
    }

}
