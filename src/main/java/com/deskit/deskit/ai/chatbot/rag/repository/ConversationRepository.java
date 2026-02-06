package com.deskit.deskit.ai.chatbot.rag.repository;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatInfo;
import com.deskit.deskit.ai.chatbot.openai.entity.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<ChatInfo, Long> {

    // 채팅상태 내림차순
    List<ChatInfo> findByStatus(ConversationStatus status);

    Optional<ChatInfo> findTopByMemberIdOrderByCreatedAtDesc(Long memberId);
}
