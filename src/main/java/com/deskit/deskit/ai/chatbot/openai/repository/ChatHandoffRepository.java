package com.deskit.deskit.ai.chatbot.openai.repository;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatHandoff;
import com.deskit.deskit.ai.chatbot.openai.entity.HandoffStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatHandoffRepository extends JpaRepository<ChatHandoff, Long> {

    boolean existsByChatIdAndStatus(Long chatId, HandoffStatus status);

    java.util.Optional<ChatHandoff> findTopByChatIdOrderByCreatedAtDesc(Long chatId);
}
