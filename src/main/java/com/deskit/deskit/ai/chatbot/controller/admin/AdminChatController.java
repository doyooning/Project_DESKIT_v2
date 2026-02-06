package com.deskit.deskit.ai.chatbot.controller.admin;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatInfo;
import com.deskit.deskit.ai.chatbot.openai.entity.ConversationStatus;
import com.deskit.deskit.ai.chatbot.rag.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Log4j2
@RestController
@RequiredArgsConstructor
public class AdminChatController {

    private final ConversationRepository conversationRepository;

    @GetMapping("/admin/chats/escalated")
    public List<ChatInfo> getEscalatedChats() {
        return conversationRepository.findByStatus(ConversationStatus.ESCALATED);
    }

    @PostMapping("/admin/chats/{conversationId}/start")
    public void startAdminChat(@PathVariable Long conversationId) {

        ChatInfo chatInfo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found. id=" + conversationId
                ));

        chatInfo.setStatus(ConversationStatus.ESCALATED);
        conversationRepository.save(chatInfo);
        log.info(chatInfo);
    }
}
