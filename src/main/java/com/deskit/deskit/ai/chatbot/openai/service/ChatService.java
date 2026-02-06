package com.deskit.deskit.ai.chatbot.openai.service;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatMessage;
import com.deskit.deskit.ai.chatbot.openai.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;

    @Transactional(readOnly = true)
    public List<ChatMessage> readAllChats(Long chatId) {
        return chatRepository.findByChatIdOrderByCreatedAtAsc(chatId);
    }

}
