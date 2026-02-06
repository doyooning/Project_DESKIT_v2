package com.deskit.deskit.directchat.controller;

import com.deskit.deskit.directchat.dto.DirectChatLatestResponse;
import com.deskit.deskit.directchat.dto.DirectChatMessageRequest;
import com.deskit.deskit.directchat.dto.DirectChatMessageResponse;
import com.deskit.deskit.directchat.service.DirectChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/direct-chats", "/api/direct-chats"})
public class DirectChatController {

    private final DirectChatService directChatService;

    @GetMapping("/latest/{memberId}")
    public DirectChatLatestResponse getLatestConversation(@PathVariable Long memberId) {
        return directChatService.getLatestConversation(memberId);
    }

    @PostMapping("/start/{memberId}")
    public DirectChatLatestResponse startConversation(@PathVariable Long memberId) {
        return directChatService.startNewConversation(memberId);
    }

    @GetMapping("/{chatId}/messages")
    public List<DirectChatMessageResponse> getMessages(@PathVariable Long chatId) {
        return directChatService.getMessages(chatId);
    }

    @PostMapping("/{chatId}/messages")
    public DirectChatMessageResponse sendMessage(
            @PathVariable Long chatId,
            @RequestBody DirectChatMessageRequest request
    ) {
        return directChatService.saveMessage(chatId, request);
    }

    @PostMapping("/{chatId}/close")
    public void closeChat(@PathVariable Long chatId) {
        directChatService.closeChat(chatId);
    }
}
