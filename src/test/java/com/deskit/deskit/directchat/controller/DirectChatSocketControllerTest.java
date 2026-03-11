package com.deskit.deskit.directchat.controller;

import com.deskit.deskit.directchat.dto.DirectChatMessageRequest;
import com.deskit.deskit.directchat.dto.DirectChatMessageResponse;
import com.deskit.deskit.directchat.service.DirectChatService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectChatSocketControllerTest {

    private final DirectChatService directChatService = mock(DirectChatService.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final DirectChatSocketController controller = new DirectChatSocketController(directChatService, messagingTemplate);

    @Test
    void sendMessageSavesAndBroadcasts() {
        DirectChatMessageRequest request = new DirectChatMessageRequest();
        request.setSender("USER");
        request.setContent("hello");
        DirectChatMessageResponse response = DirectChatMessageResponse.builder()
                .chatId(7L)
                .sender("USER")
                .content("hello")
                .build();
        when(directChatService.saveMessage(7L, request)).thenReturn(response);

        controller.sendMessage(7L, request);

        verify(directChatService).saveMessage(7L, request);
        verify(messagingTemplate).convertAndSend("/topic/direct-chats/7", response);
    }
}
