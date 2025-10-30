package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ChatRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ChatResponse;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.service.impl.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping
    ApiResponse<ChatResponse> creatMessage(@RequestBody ChatRequest request) {
        String principal = SecurityContextHolder.getContext().getAuthentication().getName();
        request.setSender(principal);

        return ApiResponse.<ChatResponse>builder()
                .code(HttpStatus.CREATED.value())
                .result(chatMessageService.createMessage(request))
                .build();
    }

    @GetMapping("/{conversationId}")
    ApiResponse<PageResponse<ChatResponse>> getMessageByConversationId(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "15") int size,
            @PathVariable String conversationId) {

        return ApiResponse.<PageResponse<ChatResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(chatMessageService.getMessageByConversationId(page, size, conversationId))
                .build();
    }

}
