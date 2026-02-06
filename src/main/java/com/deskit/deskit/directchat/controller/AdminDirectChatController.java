package com.deskit.deskit.directchat.controller;

import com.deskit.deskit.directchat.dto.DirectChatAcceptRequest;
import com.deskit.deskit.directchat.dto.DirectChatSummaryResponse;
import com.deskit.deskit.directchat.service.DirectChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/admin/direct-chats", "/api/admin/direct-chats"})
public class AdminDirectChatController {

    private final DirectChatService directChatService;

    @GetMapping("/escalated")
    public List<DirectChatSummaryResponse> getEscalatedChats() {
        return directChatService.getEscalatedChats();
    }

    @GetMapping("/active")
    public List<DirectChatSummaryResponse> getActiveChats(@RequestParam Long adminId) {
        return directChatService.getActiveChats(adminId);
    }

    @PostMapping("/{chatId}/accept")
    public DirectChatSummaryResponse acceptChat(
            @PathVariable Long chatId,
            @RequestBody DirectChatAcceptRequest request
    ) {
        return directChatService.acceptChat(chatId, request.getAdminId());
    }

    @PostMapping("/{chatId}/close")
    public void closeChat(@PathVariable Long chatId) {
        directChatService.closeChat(chatId);
    }
}
