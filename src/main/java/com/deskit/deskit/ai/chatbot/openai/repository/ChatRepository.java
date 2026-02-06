package com.deskit.deskit.ai.chatbot.openai.repository;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatIdOrderByCreatedAtAsc(Long chatId);
}
