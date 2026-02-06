package com.deskit.deskit.ai.chatbot.rag.service;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatHandoff;
import com.deskit.deskit.ai.chatbot.openai.entity.ChatInfo;
import com.deskit.deskit.ai.chatbot.openai.entity.ConversationStatus;
import com.deskit.deskit.ai.chatbot.openai.entity.HandoffStatus;
import com.deskit.deskit.ai.chatbot.openai.repository.ChatHandoffRepository;
import com.deskit.deskit.ai.chatbot.openai.repository.ChatRepository;
import com.deskit.deskit.ai.chatbot.rag.dto.ChatResponse;
import com.deskit.deskit.ai.chatbot.rag.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class AdminEscalationService {

    private final ConversationRepository conversationRepository;
    private final ChatRepository chatRepository;
    private final ChatHandoffRepository chatHandoffRepository;
    private final ChatSaveService chatSaveService;
    private final ChatMemoryRepository chatMemoryRepository;

    @Transactional
    public ChatResponse escalate(String question, long conversationId, String userId) {

        String escalateMessage = "채팅이 관리자로 이관되었어요. 관리자가 곧 답변 드릴 예정이에요.";

        ChatInfo chatInfo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found. id=" + conversationId
                ));

        chatInfo.setStatus(ConversationStatus.ESCALATED);
        conversationRepository.save(chatInfo);
        log.info(chatInfo);

        createHandoffIfAbsent(chatInfo.getChatId());

        chatSaveService.saveChat(chatInfo.getChatId(), question, escalateMessage);
        chatSaveService.saveChatMemory(userId, question, chatMemoryRepository);

        return ChatResponse.builder()
                .answer(escalateMessage)
                .escalated(true)
                .build();
    }

    private void createHandoffIfAbsent(Long chatId) {
        if (chatHandoffRepository.existsByChatIdAndStatus(chatId, HandoffStatus.ADMIN_WAITING)) {
            return;
        }

        ChatHandoff handoff = new ChatHandoff();
        handoff.setChatId(chatId);
        handoff.setStatus(HandoffStatus.ADMIN_WAITING);
        chatHandoffRepository.save(handoff);
    }
}
