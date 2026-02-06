package com.deskit.deskit.ai.chatbot.controller.admin;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatInfo;
import com.deskit.deskit.ai.chatbot.openai.entity.ConversationStatus;
import com.deskit.deskit.ai.chatbot.rag.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/conversations")
public class AdminConversationController {

    private final ConversationRepository conversationRepository;

    // 이관된 채팅 목록 조회
    @GetMapping("/escalated")
    public List<ChatInfo> getEscalatedConversations() {
        return conversationRepository.findByStatus(ConversationStatus.ESCALATED);
    }

    @PostMapping("/{conversationId}/start")
    public void startConversation(@PathVariable Long conversationId) {

        ChatInfo chatInfo = conversationRepository.findById(conversationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Conversation not found: " + conversationId)
                );

        chatInfo.setStatus(ConversationStatus.ADMIN_ACTIVE);
        conversationRepository.save(chatInfo);
    }

}

