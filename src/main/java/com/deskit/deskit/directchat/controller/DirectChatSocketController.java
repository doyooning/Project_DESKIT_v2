package com.deskit.deskit.directchat.controller;

import com.deskit.deskit.directchat.dto.DirectChatMessageRequest;
import com.deskit.deskit.directchat.dto.DirectChatMessageResponse;
import com.deskit.deskit.directchat.service.DirectChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DirectChatSocketController {

    private final DirectChatService directChatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/direct-chats/{chatId}")
    public void sendMessage(
            @DestinationVariable Long chatId,
            DirectChatMessageRequest request
    ) {
        DirectChatMessageResponse response = directChatService.saveMessage(chatId, request);
        messagingTemplate.convertAndSend("/topic/direct-chats/" + chatId, response);
    }
}
