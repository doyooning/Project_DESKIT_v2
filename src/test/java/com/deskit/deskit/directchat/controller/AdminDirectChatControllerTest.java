package com.deskit.deskit.directchat.controller;

import com.deskit.deskit.directchat.dto.DirectChatAcceptRequest;
import com.deskit.deskit.directchat.dto.DirectChatSummaryResponse;
import com.deskit.deskit.directchat.service.DirectChatService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDirectChatControllerTest {

    private final DirectChatService directChatService = mock(DirectChatService.class);
    private final AdminDirectChatController controller = new AdminDirectChatController(directChatService);

    @Test
    void getEscalatedChatsDelegatesToService() {
        DirectChatSummaryResponse summary = DirectChatSummaryResponse.builder().chatId(1L).status("ESCALATED").build();
        when(directChatService.getEscalatedChats()).thenReturn(List.of(summary));

        List<DirectChatSummaryResponse> result = controller.getEscalatedChats();

        assertThat(result).containsExactly(summary);
    }

    @Test
    void getActiveChatsDelegatesToService() {
        DirectChatSummaryResponse summary = DirectChatSummaryResponse.builder().chatId(2L).status("ADMIN_ACTIVE").build();
        when(directChatService.getActiveChats(9L)).thenReturn(List.of(summary));

        List<DirectChatSummaryResponse> result = controller.getActiveChats(9L);

        assertThat(result).containsExactly(summary);
    }

    @Test
    void acceptChatDelegatesToService() {
        DirectChatAcceptRequest request = new DirectChatAcceptRequest();
        request.setAdminId(100L);
        DirectChatSummaryResponse expected = DirectChatSummaryResponse.builder().chatId(3L).assignedAdminId(100L).build();
        when(directChatService.acceptChat(3L, 100L)).thenReturn(expected);

        DirectChatSummaryResponse result = controller.acceptChat(3L, request);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void closeChatDelegatesToService() {
        controller.closeChat(4L);
        verify(directChatService).closeChat(4L);
    }
}
