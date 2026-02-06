package com.deskit.deskit.ai.chatbot.rag.service;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatMessage;
import com.deskit.deskit.ai.chatbot.openai.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class ChatSaveService {

    private final ChatRepository chatRepository;

    // chatMessage를 저장
    public void saveChat(Long chatId, String question, String answer) {

        // 사용자 메시지 저장
        ChatMessage userChat = new ChatMessage();
        userChat.setChatId(chatId);
        userChat.setType(MessageType.USER);
        userChat.setContent(question);

        // 챗봇 메시지 저장
        ChatMessage assistantChat = new ChatMessage();
        assistantChat.setChatId(chatId);
        assistantChat.setType(MessageType.ASSISTANT);
        assistantChat.setContent(answer);

        chatRepository.saveAll(List.of(userChat, assistantChat));
    }

    // chatmemory에 저장
    public void saveChatMemory(String memberId, String text, ChatMemoryRepository chatMemoryRepository) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .chatMemoryRepository(chatMemoryRepository)
                .build();
        chatMemory.add(memberId, new UserMessage(text));
    }
}
