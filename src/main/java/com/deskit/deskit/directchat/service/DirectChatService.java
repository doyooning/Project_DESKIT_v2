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
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DirectChatService {

    private final ConversationRepository conversationRepository;
    private final ChatRepository chatRepository;
    private final ChatHandoffRepository chatHandoffRepository;
    private final MemberRepository memberRepository;
    private final SellerRepository sellerRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public List<DirectChatSummaryResponse> getEscalatedChats() {
        return conversationRepository.findByStatus(ConversationStatus.ESCALATED).stream()
                .map(chatInfo -> {
                    ChatHandoff handoff = chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(chatInfo.getChatId())
                            .orElse(null);
                    String loginId = resolveLoginId(chatInfo.getMemberId());
                    return DirectChatSummaryResponse.builder()
                            .chatId(chatInfo.getChatId())
                            .memberId(chatInfo.getMemberId())
                            .loginId(loginId)
                            .status(chatInfo.getStatus().name())
                            .createdAt(chatInfo.getCreatedAt())
                            .assignedAdminId(handoff != null ? handoff.getAssignedAdminId() : null)
                            .handoffStatus(handoff != null ? handoff.getStatus().name() : null)
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DirectChatSummaryResponse> getActiveChats(Long adminId) {
        if (adminId == null) {
            return List.of();
        }
        return conversationRepository.findByStatus(ConversationStatus.ADMIN_ACTIVE).stream()
                .map(chatInfo -> {
                    ChatHandoff handoff = chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(chatInfo.getChatId())
                            .orElse(null);
                    if (handoff == null || !adminId.equals(handoff.getAssignedAdminId())) {
                        return null;
                    }
                    String loginId = resolveLoginId(chatInfo.getMemberId());
                    return DirectChatSummaryResponse.builder()
                            .chatId(chatInfo.getChatId())
                            .memberId(chatInfo.getMemberId())
                            .loginId(loginId)
                            .status(chatInfo.getStatus().name())
                            .createdAt(chatInfo.getCreatedAt())
                            .assignedAdminId(handoff.getAssignedAdminId())
                            .handoffStatus(handoff.getStatus().name())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public DirectChatLatestResponse getLatestConversation(Long memberId) {
        ChatInfo chatInfo = conversationRepository.findTopByMemberIdOrderByCreatedAtDesc(memberId)
                .orElseGet(() -> {
                    ChatInfo created = new ChatInfo();
                    created.setMemberId(memberId);
                    created.setStatus(ConversationStatus.BOT_ACTIVE);
                    return conversationRepository.save(created);
                });
        return DirectChatLatestResponse.builder()
                .chatId(chatInfo.getChatId())
                .status(chatInfo.getStatus().name())
                .build();
    }

    @Transactional
    public DirectChatLatestResponse startNewConversation(Long memberId) {
        ChatInfo chatInfo = conversationRepository.findTopByMemberIdOrderByCreatedAtDesc(memberId)
                .filter(latest -> latest.getStatus() != ConversationStatus.CLOSED)
                .orElseGet(() -> {
                    ChatInfo created = new ChatInfo();
                    created.setMemberId(memberId);
                    created.setStatus(ConversationStatus.BOT_ACTIVE);
                    return conversationRepository.save(created);
                });

        return DirectChatLatestResponse.builder()
                .chatId(chatInfo.getChatId())
                .status(chatInfo.getStatus().name())
                .build();
    }

    @Transactional
    public DirectChatSummaryResponse acceptChat(Long chatId, Long adminId) {
        ChatInfo chatInfo = conversationRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found. id=" + chatId));

        chatInfo.setStatus(ConversationStatus.ADMIN_ACTIVE);
        conversationRepository.save(chatInfo);

        ChatHandoff handoff = chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(chatId)
                .orElseGet(() -> {
                    ChatHandoff created = new ChatHandoff();
                    created.setChatId(chatId);
                    created.setStatus(HandoffStatus.ADMIN_WAITING);
                    return created;
                });
        handoff.setAssignedAdminId(adminId);
        handoff.setStatus(HandoffStatus.ADMIN_CHECKED);
        chatHandoffRepository.save(handoff);

        String loginId = resolveLoginId(chatInfo.getMemberId());
        return DirectChatSummaryResponse.builder()
                .chatId(chatInfo.getChatId())
                .memberId(chatInfo.getMemberId())
                .loginId(loginId)
                .status(chatInfo.getStatus().name())
                .createdAt(chatInfo.getCreatedAt())
                .assignedAdminId(handoff.getAssignedAdminId())
                .handoffStatus(handoff.getStatus().name())
                .build();
    }

    @Transactional
    public void closeChat(Long chatId) {
        ChatInfo chatInfo = conversationRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found. id=" + chatId));

        if (chatInfo.getStatus() == ConversationStatus.CLOSED) {
            return;
        }

        chatInfo.setStatus(ConversationStatus.CLOSED);
        conversationRepository.save(chatInfo);

        ChatMessage closeMessage = new ChatMessage();
        closeMessage.setChatId(chatId);
        closeMessage.setType(MessageType.SYSTEM);
        closeMessage.setContent("상담이 종료되었습니다.");
        chatRepository.save(closeMessage);

        DirectChatMessageResponse response = DirectChatMessageResponse.builder()
                .messageId(closeMessage.getMessageId())
                .chatId(closeMessage.getChatId())
                .sender(resolveSender(closeMessage.getType()))
                .content(closeMessage.getContent())
                .createdAt(closeMessage.getCreatedAt())
                .build();
        messagingTemplate.convertAndSend("/topic/direct-chats/" + chatId, response);

        chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(chatId)
                .ifPresent(handoff -> {
                    handoff.setStatus(HandoffStatus.ADMIN_ANSWERED);
                    chatHandoffRepository.save(handoff);
                });
    }

    @Transactional(readOnly = true)
    public List<DirectChatMessageResponse> getMessages(Long chatId) {
        return chatRepository.findByChatIdOrderByCreatedAtAsc(chatId).stream()
                .map(message -> DirectChatMessageResponse.builder()
                        .messageId(message.getMessageId())
                        .chatId(message.getChatId())
                        .sender(resolveSender(message.getType()))
                        .content(message.getContent())
                        .createdAt(message.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public DirectChatMessageResponse saveMessage(Long chatId, DirectChatMessageRequest request) {
        ChatMessage message = new ChatMessage();
        message.setChatId(chatId);
        message.setContent(request.getContent());
        message.setType(resolveMessageType(request.getSender()));
        chatRepository.save(message);

        if ("ADMIN".equalsIgnoreCase(request.getSender())) {
            chatHandoffRepository.findTopByChatIdOrderByCreatedAtDesc(chatId)
                    .ifPresent(handoff -> {
                        handoff.setStatus(HandoffStatus.ADMIN_ANSWERED);
                        chatHandoffRepository.save(handoff);
                    });
        }

        return DirectChatMessageResponse.builder()
                .messageId(message.getMessageId())
                .chatId(message.getChatId())
                .sender(resolveSender(message.getType()))
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private MessageType resolveMessageType(String sender) {
        if (sender == null) {
            return MessageType.USER;
        }
        if ("SYSTEM".equalsIgnoreCase(sender)) {
            return MessageType.SYSTEM;
        }
        if ("USER".equalsIgnoreCase(sender)) {
            return MessageType.USER;
        }
        return MessageType.ASSISTANT;
    }

    private String resolveSender(MessageType type) {
        if (type == null) {
            return "SYSTEM";
        }
        return switch (type) {
            case USER -> "USER";
            case SYSTEM -> "SYSTEM";
            default -> "ADMIN";
        };
    }

    private String resolveLoginId(Long memberId) {
        if (memberId == null) {
            return null;
        }
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member != null) {
            return member.getLoginId();
        }
        Seller seller = sellerRepository.findById(memberId).orElse(null);
        return seller != null ? seller.getLoginId() : null;
    }
}
