package com.deskit.deskit.livechat.service;

import com.deskit.deskit.livechat.dto.LiveChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Service
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessageSendingOperations messagingTemplate;

    public RedisSubscriber(ObjectMapper objectMapper, RedisTemplate<String, Object> redisTemplate, SimpMessageSendingOperations messagingTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. Redis에서 온 메시지(JSON String)를 가져옴
            String publishMessage = (String) redisTemplate.getStringSerializer().deserialize(message.getBody());

            // 2. JSON String을 ChatMessage 객체로 변환
            LiveChatMessage roomMessage = objectMapper.readValue(publishMessage, LiveChatMessage.class);

            // 3. 로그 출력 (null 대신 정확한 정보를 출력)
            if (roomMessage.getType() == LiveChatMessage.MessageType.CHAT) {
                System.out.println("[CHAT] 방(" + roomMessage.getRoomId() + "): " + roomMessage.getContent());
            } else if (roomMessage.getType() == LiveChatMessage.MessageType.CREATE) {
                System.out.println("[CREATE] 새 방송 생성됨: " + roomMessage.getRoomId());
            } else if (roomMessage.getType() == LiveChatMessage.MessageType.JOIN) {
                System.out.println("[JOIN] 입장: " + roomMessage.getSender() + " -> " + roomMessage.getRoomId());
            } else {
                System.out.println("[OTHER] " + roomMessage.getType());
            }

            // 4. 구독자들(웹소켓 연결된 클라이언트들)에게 메시지 전달
            messagingTemplate.convertAndSend("/topic/public", roomMessage);

        } catch (Exception e) {
            System.err.println("❌ Redis 메시지 처리 중 오류 발생: " + e.getMessage());
        }
    }
}