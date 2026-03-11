package com.deskit.deskit.directchat.service;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatHandoff;
import com.deskit.deskit.ai.chatbot.openai.entity.ChatInfo;
import com.deskit.deskit.ai.chatbot.openai.entity.ChatMessage;
import com.deskit.deskit.ai.chatbot.openai.entity.ConversationStatus;
import com.deskit.deskit.ai.chatbot.openai.entity.HandoffStatus;
import com.deskit.deskit.ai.chatbot.openai.repository.ChatHandoffRepository;
import com.deskit.deskit.ai.chatbot.openai.repository.ChatRepository;
import com.deskit.deskit.ai.chatbot.rag.repository.ConversationRepository;
import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.directchat.dto.DirectChatLatestResponse;
import com.deskit.deskit.directchat.dto.DirectChatMessageRequest;
import com.deskit.deskit.directchat.dto.DirectChatMessageResponse;
import com.deskit.deskit.directchat.dto.DirectChatSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DirectChatServiceTest {

    private ConversationRepository conversationRepository;
    private ChatRepository chatRepository;
    private ChatHandoffRepository chatHandoffRepository;
    private MemberRepository memberRepository;
    private SellerRepository sellerRepository;
    private SimpMessagingTemplate messagingTemplate;
    private DirectChatService service;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        chatRepository = mock(ChatRepository.class);
        chatHandoffRepository = mock(ChatHandoffRepository.class);
        memberRepository = mock(MemberRepository.class);
        sellerRepository = mock(SellerRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        service = new DirectChatService(
                conversationRepository,
                chatRepository,
                chatHandoffRepository,
                memberRepository,
                sellerRepository,
                messagingTemplate
        );
    }

    @Test
    void getEscalatedChatsMapsHandoffAndLoginId() {
        ChatInfo one = chatInfo(1L, 10L, ConversationStatus.ESCALATED);
        ChatInfo two = chatInfo(2L, 20L, ConversationStatus.ESCALATED);
        ChatInfo three = chatInfo(3L, null, ConversationStatus.ESCALATED);
        when(conversationRepository.findByStatus(ConversationStatus.ESCALATED)).thenReturn(List.of(one, two, three));
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(handoff(1L, 99L, HandoffStatus.ADMIN_WAITING)));
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.empty());
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.empty());
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member(10L, "member10")));
        when(memberRepository.findById(20L)).thenReturn(Optional.empty());
        when(sellerRepository.findById(20L)).thenReturn(Optional.of(seller(20L, "seller20")));

        List<DirectChatSummaryResponse> result = service.getEscalatedChats();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getLoginId()).isEqualTo("member10");
        assertThat(result.get(0).getAssignedAdminId()).isEqualTo(99L);
        assertThat(result.get(1).getLoginId()).isEqualTo("seller20");
        assertThat(result.get(1).getAssignedAdminId()).isNull();
        assertThat(result.get(2).getLoginId()).isNull();
        verify(memberRepository, never()).findById(null);
    }

    @Test
    void getActiveChatsReturnsEmptyWhenAdminIdIsNull() {
        assertThat(service.getActiveChats(null)).isEmpty();
        verifyNoInteractions(conversationRepository, chatHandoffRepository);
    }

    @Test
    void getActiveChatsFiltersOnlyAssignedAdmin() {
        ChatInfo one = chatInfo(11L, 1L, ConversationStatus.ADMIN_ACTIVE);
        ChatInfo two = chatInfo(12L, 2L, ConversationStatus.ADMIN_ACTIVE);
        ChatInfo three = chatInfo(13L, 3L, ConversationStatus.ADMIN_ACTIVE);
        when(conversationRepository.findByStatus(ConversationStatus.ADMIN_ACTIVE)).thenReturn(List.of(one, two, three));
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.empty());
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(12L)).thenReturn(Optional.of(handoff(12L, 50L, HandoffStatus.ADMIN_CHECKED)));
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(13L)).thenReturn(Optional.of(handoff(13L, 7L, HandoffStatus.ADMIN_CHECKED)));
        when(memberRepository.findById(3L)).thenReturn(Optional.empty());
        when(sellerRepository.findById(3L)).thenReturn(Optional.empty());

        List<DirectChatSummaryResponse> result = service.getActiveChats(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChatId()).isEqualTo(13L);
        assertThat(result.get(0).getLoginId()).isNull();
    }

    @Test
    void getLatestConversationReturnsExistingConversation() {
        ChatInfo existing = chatInfo(5L, 100L, ConversationStatus.ADMIN_ACTIVE);
        when(conversationRepository.findTopByMemberIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.of(existing));

        DirectChatLatestResponse result = service.getLatestConversation(100L);

        assertThat(result.getChatId()).isEqualTo(5L);
        assertThat(result.getStatus()).isEqualTo("ADMIN_ACTIVE");
    }

    @Test
    void getLatestConversationCreatesWhenMissing() {
        when(conversationRepository.findTopByMemberIdOrderByCreatedAtDesc(101L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(ChatInfo.class))).thenAnswer(invocation -> {
            ChatInfo saved = invocation.getArgument(0);
            saved.setChatId(6L);
            return saved;
        });

        DirectChatLatestResponse result = service.getLatestConversation(101L);

        assertThat(result.getChatId()).isEqualTo(6L);
        assertThat(result.getStatus()).isEqualTo("BOT_ACTIVE");
    }

    @Test
    void startNewConversationReturnsLatestWhenNotClosed() {
        ChatInfo latest = chatInfo(21L, 201L, ConversationStatus.BOT_ACTIVE);
        when(conversationRepository.findTopByMemberIdOrderByCreatedAtDesc(201L)).thenReturn(Optional.of(latest));

        DirectChatLatestResponse result = service.startNewConversation(201L);

        assertThat(result.getChatId()).isEqualTo(21L);
        assertThat(result.getStatus()).isEqualTo("BOT_ACTIVE");
        verify(conversationRepository, never()).save(any(ChatInfo.class));
    }

    @Test
    void startNewConversationCreatesWhenLatestIsClosed() {
        ChatInfo latestClosed = chatInfo(22L, 202L, ConversationStatus.CLOSED);
        when(conversationRepository.findTopByMemberIdOrderByCreatedAtDesc(202L)).thenReturn(Optional.of(latestClosed));
        when(conversationRepository.save(any(ChatInfo.class))).thenAnswer(invocation -> {
            ChatInfo saved = invocation.getArgument(0);
            saved.setChatId(23L);
            return saved;
        });

        DirectChatLatestResponse result = service.startNewConversation(202L);

        assertThat(result.getChatId()).isEqualTo(23L);
        assertThat(result.getStatus()).isEqualTo("BOT_ACTIVE");
    }

    @Test
    void acceptChatThrowsWhenConversationMissing() {
        when(conversationRepository.findById(1000L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptChat(1000L, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void acceptChatUsesExistingHandoff() {
        ChatInfo info = chatInfo(30L, 1L, ConversationStatus.ESCALATED);
        ChatHandoff handoff = handoff(30L, null, HandoffStatus.ADMIN_WAITING);
        when(conversationRepository.findById(30L)).thenReturn(Optional.of(info));
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(30L)).thenReturn(Optional.of(handoff));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member(1L, "m1")));

        DirectChatSummaryResponse result = service.acceptChat(30L, 200L);

        assertThat(result.getStatus()).isEqualTo("ADMIN_ACTIVE");
        assertThat(result.getAssignedAdminId()).isEqualTo(200L);
        assertThat(result.getHandoffStatus()).isEqualTo("ADMIN_CHECKED");
        assertThat(result.getLoginId()).isEqualTo("m1");
    }

    @Test
    void acceptChatCreatesHandoffWhenMissing() {
        ChatInfo info = chatInfo(31L, 2L, ConversationStatus.ESCALATED);
        when(conversationRepository.findById(31L)).thenReturn(Optional.of(info));
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(31L)).thenReturn(Optional.empty());
        when(memberRepository.findById(2L)).thenReturn(Optional.empty());
        when(sellerRepository.findById(2L)).thenReturn(Optional.of(seller(2L, "s2")));

        DirectChatSummaryResponse result = service.acceptChat(31L, 201L);

        assertThat(result.getAssignedAdminId()).isEqualTo(201L);
        assertThat(result.getHandoffStatus()).isEqualTo("ADMIN_CHECKED");
        assertThat(result.getLoginId()).isEqualTo("s2");
    }

    @Test
    void closeChatThrowsWhenConversationMissing() {
        when(conversationRepository.findById(41L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.closeChat(41L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void closeChatReturnsWhenAlreadyClosed() {
        ChatInfo closed = chatInfo(42L, 1L, ConversationStatus.CLOSED);
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(closed));

        service.closeChat(42L);

        verify(conversationRepository, never()).save(any(ChatInfo.class));
        verifyNoInteractions(chatRepository, messagingTemplate);
    }

    @Test
    void closeChatSendsSystemMessageAndUpdatesHandoff() {
        ChatInfo active = chatInfo(43L, 1L, ConversationStatus.ADMIN_ACTIVE);
        ChatHandoff handoff = handoff(43L, 300L, HandoffStatus.ADMIN_CHECKED);
        when(conversationRepository.findById(43L)).thenReturn(Optional.of(active));
        when(chatRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setMessageId(501L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(43L)).thenReturn(Optional.of(handoff));

        service.closeChat(43L);

        assertThat(active.getStatus()).isEqualTo(ConversationStatus.CLOSED);
        verify(conversationRepository).save(active);
        verify(chatRepository).save(any(ChatMessage.class));
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/direct-chats/43"), any(DirectChatMessageResponse.class));
        assertThat(handoff.getStatus()).isEqualTo(HandoffStatus.ADMIN_ANSWERED);
        verify(chatHandoffRepository).save(handoff);
    }

    @Test
    void closeChatSendsSystemMessageWithoutHandoff() {
        ChatInfo active = chatInfo(44L, 1L, ConversationStatus.BOT_ACTIVE);
        when(conversationRepository.findById(44L)).thenReturn(Optional.of(active));
        when(chatRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setMessageId(502L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(44L)).thenReturn(Optional.empty());

        service.closeChat(44L);

        verify(chatHandoffRepository, never()).save(any(ChatHandoff.class));
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/direct-chats/44"), any(DirectChatMessageResponse.class));
    }

    @Test
    void getMessagesMapsSenderTypes() {
        ChatMessage one = message(1L, 77L, MessageType.USER, "u");
        ChatMessage two = message(2L, 77L, MessageType.SYSTEM, "s");
        ChatMessage three = message(3L, 77L, MessageType.ASSISTANT, "a");
        ChatMessage four = message(4L, 77L, null, "n");
        when(chatRepository.findByChatIdOrderByCreatedAtAsc(77L)).thenReturn(List.of(one, two, three, four));

        List<DirectChatMessageResponse> result = service.getMessages(77L);

        assertThat(result).hasSize(4);
        assertThat(result.get(0).getSender()).isEqualTo("USER");
        assertThat(result.get(1).getSender()).isEqualTo("SYSTEM");
        assertThat(result.get(2).getSender()).isEqualTo("ADMIN");
        assertThat(result.get(3).getSender()).isEqualTo("SYSTEM");
    }

    @Test
    void saveMessageMapsSenderTypes() {
        when(chatRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setMessageId(600L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });

        DirectChatMessageRequest nullSender = new DirectChatMessageRequest();
        nullSender.setSender(null);
        nullSender.setContent("n");
        DirectChatMessageRequest systemSender = new DirectChatMessageRequest();
        systemSender.setSender("SYSTEM");
        systemSender.setContent("s");
        DirectChatMessageRequest userSender = new DirectChatMessageRequest();
        userSender.setSender("USER");
        userSender.setContent("u");
        DirectChatMessageRequest unknownSender = new DirectChatMessageRequest();
        unknownSender.setSender("BOT");
        unknownSender.setContent("a");

        assertThat(service.saveMessage(88L, nullSender).getSender()).isEqualTo("USER");
        assertThat(service.saveMessage(88L, systemSender).getSender()).isEqualTo("SYSTEM");
        assertThat(service.saveMessage(88L, userSender).getSender()).isEqualTo("USER");
        assertThat(service.saveMessage(88L, unknownSender).getSender()).isEqualTo("ADMIN");
    }

    @Test
    void saveMessageUpdatesHandoffOnlyForAdminSender() {
        when(chatRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setMessageId(700L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        ChatHandoff handoff = handoff(90L, 1L, HandoffStatus.ADMIN_CHECKED);
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(90L)).thenReturn(Optional.of(handoff));
        when(chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(91L)).thenReturn(Optional.empty());

        DirectChatMessageRequest admin = new DirectChatMessageRequest();
        admin.setSender("ADMIN");
        admin.setContent("answer");
        DirectChatMessageRequest member = new DirectChatMessageRequest();
        member.setSender("USER");
        member.setContent("question");

        service.saveMessage(90L, admin);
        service.saveMessage(91L, admin);
        service.saveMessage(90L, member);

        assertThat(handoff.getStatus()).isEqualTo(HandoffStatus.ADMIN_ANSWERED);
        verify(chatHandoffRepository).save(handoff);
    }

    private ChatInfo chatInfo(Long chatId, Long memberId, ConversationStatus status) {
        ChatInfo info = new ChatInfo();
        info.setChatId(chatId);
        info.setMemberId(memberId);
        info.setStatus(status);
        info.setCreatedAt(LocalDateTime.now());
        return info;
    }

    private ChatHandoff handoff(Long chatId, Long adminId, HandoffStatus status) {
        ChatHandoff handoff = new ChatHandoff();
        handoff.setChatId(chatId);
        handoff.setAssignedAdminId(adminId);
        handoff.setStatus(status);
        handoff.setCreatedAt(LocalDateTime.now());
        return handoff;
    }

    private ChatMessage message(Long messageId, Long chatId, MessageType type, String content) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setChatId(chatId);
        message.setType(type);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private Member member(Long id, String loginId) {
        return Member.builder()
                .memberId(id)
                .loginId(loginId)
                .name("name")
                .phone("010")
                .role("ROLE_MEMBER")
                .build();
    }

    private Seller seller(Long id, String loginId) {
        return Seller.builder()
                .sellerId(id)
                .loginId(loginId)
                .name("name")
                .phone("010")
                .build();
    }
}
