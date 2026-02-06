package com.deskit.deskit.livechat.controller;

import com.deskit.deskit.livechat.dto.LiveChatMessageDTO;
import com.deskit.deskit.livechat.dto.LiveMessageType;
import com.deskit.deskit.livechat.service.LiveChatService;
import com.deskit.deskit.livehost.service.BroadcastService;
import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

@RestController
@Slf4j
@RequiredArgsConstructor
public class LiveChatController {

    private final SimpMessageSendingOperations messagingTemplate;
    private final LiveChatService chatService;
    private final BroadcastService broadcastService;
    private final MemberRepository memberRepository;

    @MessageMapping("/chat/message")
    public void handleMessage(LiveChatMessageDTO message, Principal principal, SimpMessageHeaderAccessor accessor) {
        if (message.getType() == LiveMessageType.TALK && message.getBroadcastId() != null) {
            String loginId = message.getMemberEmail();
            if (loginId != null && !loginId.isBlank()) {
                Member member = memberRepository.findByLoginId(loginId);
                if (member != null && !broadcastService.canChat(message.getBroadcastId(), member.getMemberId())) {
                    log.debug("livechat.blocked broadcastId={} memberId={} reason=SANCTIONED",
                            message.getBroadcastId(),
                            member.getMemberId());
                    return;
                }
            }
        }
        String original = message.getContent();
        String filtered = chatService.filterContent(original);
        message.setContent(filtered);
        message.setWorld(!Objects.equals(original, filtered));
        message.setRawContent(original);
        message.setSenderRole(resolveRole(accessor));

        if (message.getSentAt() == null) {
            message.setSentAt(System.currentTimeMillis());
        }
        String principalName = principal != null ? principal.getName() : "anonymous";
        log.debug("livechat.in broadcastId={} sender={} type={} sentAt={} principal={} content={}",
                message.getBroadcastId(),
                message.getSender(),
                message.getType(),
                message.getSentAt(),
                principalName,
                message.getContent());

        chatService.saveMessageAsync(message);
        chatService.cacheRecentMessage(message);

        messagingTemplate.convertAndSend("/sub/chat/" + message.getBroadcastId(), message);
    }

    @GetMapping("/api/livechats/{broadcastId}/recent")
    public List<LiveChatMessageDTO> getRecentTalks(
            @PathVariable Long broadcastId,
            @RequestParam(name = "seconds", required = false) Long seconds
    ) {
        log.debug("livechat.recent.request broadcastId={} seconds={}", broadcastId, seconds);
        List<LiveChatMessageDTO> result = chatService.getRecentTalks(broadcastId, seconds);
        log.debug("livechat.recent.response broadcastId={} count={}", broadcastId, result.size());
        return result;
    }

    private String resolveRole(SimpMessageHeaderAccessor accessor) {
        if (accessor == null || accessor.getSessionAttributes() == null) {
            return null;
        }
        Object role = accessor.getSessionAttributes().get("role");
        return role instanceof String ? (String) role : null;
    }
}
