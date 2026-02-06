package com.deskit.deskit.ai.chatbot.openai.service;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatInfo;
import com.deskit.deskit.ai.chatbot.openai.entity.ConversationStatus;
import com.deskit.deskit.ai.chatbot.rag.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;

    @Transactional
    public ChatInfo getOrCreateActiveConversation(Long memberId) {

        return conversationRepository
                .findTopByMemberIdOrderByCreatedAtDesc(memberId)
                .map(latest -> {
                    if (latest.getStatus() == ConversationStatus.BOT_ACTIVE) {
                        return latest;
                    }
                    if (latest.getStatus() == ConversationStatus.CLOSED) {
                        ChatInfo c = new ChatInfo();
                        c.setMemberId(memberId);
                        c.setStatus(ConversationStatus.BOT_ACTIVE);
                        return conversationRepository.save(c);
                    }
                    return latest;
                })
                .orElseGet(() -> {
                    ChatInfo c = new ChatInfo();
                    c.setMemberId(memberId);
                    c.setStatus(ConversationStatus.BOT_ACTIVE);
                    return conversationRepository.save(c);
                });
    }

    @Transactional
    public ChatInfo getLatestConversation(Long memberId) {
        return getOrCreateActiveConversation(memberId);
    }

    @Transactional(readOnly = true)
    public Optional<ChatInfo> findLatestConversation(Long memberId) {
        return conversationRepository.findTopByMemberIdOrderByCreatedAtDesc(memberId);
    }

}

