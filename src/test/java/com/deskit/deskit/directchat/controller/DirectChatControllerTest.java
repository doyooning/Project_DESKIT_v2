package com.deskit.deskit.directchat.controller;

import com.deskit.deskit.directchat.dto.DirectChatLatestResponse;
import com.deskit.deskit.directchat.dto.DirectChatMessageRequest;
import com.deskit.deskit.directchat.dto.DirectChatMessageResponse;
import com.deskit.deskit.directchat.service.DirectChatService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectChatControllerTest {

    private final DirectChatService directChatService = mock(DirectChatService.class);
    private final DirectChatController controller = new DirectChatController(directChatService);

    @Test
    void getLatestConversationDelegatesToService() {
        DirectChatLatestResponse expected = DirectChatLatestResponse.builder().chatId(1L).status("BOT_ACTIVE").build();
        when(directChatService.getLatestConversation(10L)).thenReturn(expected);

        DirectChatLatestResponse result = controller.getLatestConversation(10L);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void startConversationDelegatesToService() {
        DirectChatLatestResponse expected = DirectChatLatestResponse.builder().chatId(2L).status("BOT_ACTIVE").build();
        when(directChatService.startNewConversation(11L)).thenReturn(expected);

        DirectChatLatestResponse result = controller.startConversation(11L);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getMessagesDelegatesToService() {
        DirectChatMessageResponse message = DirectChatMessageResponse.builder().messageId(1L).chatId(3L).sender("USER").content("hi").build();
        when(directChatService.getMessages(3L)).thenReturn(List.of(message));

        List<DirectChatMessageResponse> result = controller.getMessages(3L);

        assertThat(result).containsExactly(message);
    }

    @Test
    void sendMessageDelegatesToService() {
        DirectChatMessageRequest request = new DirectChatMessageRequest();
        request.setSender("USER");
        request.setContent("hello");
        DirectChatMessageResponse expected = DirectChatMessageResponse.builder().messageId(2L).chatId(4L).sender("USER").content("hello").build();
        when(directChatService.saveMessage(4L, request)).thenReturn(expected);

        DirectChatMessageResponse result = controller.sendMessage(4L, request);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void closeChatDelegatesToService() {
        controller.closeChat(5L);
        verify(directChatService).closeChat(5L);
    }
}
