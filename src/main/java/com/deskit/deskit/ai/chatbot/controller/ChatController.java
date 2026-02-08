package com.deskit.deskit.ai.chatbot.controller;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.ai.chatbot.openai.entity.ChatMessage;
import com.deskit.deskit.ai.chatbot.openai.entity.ChatInfo;
import com.deskit.deskit.ai.chatbot.openai.service.ChatService;
import com.deskit.deskit.ai.chatbot.openai.service.ConversationService;
import com.deskit.deskit.ai.chatbot.openai.service.OpenAIService;
import com.deskit.deskit.ai.chatbot.rag.dto.ChatRequest;
import com.deskit.deskit.ai.chatbot.rag.dto.ChatResponse;
import com.deskit.deskit.ai.chatbot.rag.entity.RouteDecision;
import com.deskit.deskit.ai.chatbot.rag.service.AdminEscalationService;
import com.deskit.deskit.ai.chatbot.rag.service.ChatRoutingService;
import com.deskit.deskit.ai.chatbot.rag.service.RagIngestService;
import com.deskit.deskit.ai.chatbot.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Log4j2
@Controller
@RequestMapping({"/api", ""})
@RequiredArgsConstructor
public class ChatController {

    private final RagIngestService ragIngestService;
    private final OpenAIService openAIService;
    private final ChatService chatService;
    private final RagService ragService;
    private final ChatRoutingService chatRoutingService;
    private final ConversationService conversationService;
    private final MemberRepository memberRepository;
    private final AdminEscalationService adminEscalationService;

    private static final String ESCALATION_TRIGGER = "관리자 연결";

    // 챗봇 페이지 이동
//    @GetMapping("/chat")
//    public String chatPage() {
//        return "chat";
//    }

    // 스트림
    @ResponseBody
    @PostMapping("/chat")
    public ChatResponse chat(
            @AuthenticationPrincipal CustomOAuth2User user,
            @RequestBody ChatRequest request
    ) {

        String question = request.getQuestion();
        Long memberId = resolveMemberId(user);
        if (question == null || question.isBlank()) {
            log.warn("Empty question received: {}", request);
            return null;
        }

        // 현재 진행 중인 상태 조회 or 생성
        ChatInfo chatInfo = conversationService.getOrCreateActiveConversation(memberId);
        if (chatInfo.getStatus() != com.deskit.deskit.ai.chatbot.openai.entity.ConversationStatus.BOT_ACTIVE) {
            return ChatResponse.builder()
                    .answer("채팅이 관리자에게 전달되었습니다. 관리자가 곧 상담을 진행하겠습니다.")
                    .escalated(true)
                    .build();
        }
        String normalized = question.trim();
        if (ESCALATION_TRIGGER.equals(normalized)) {
            log.info("Escalation triggered");
            return adminEscalationService.escalate(normalized, chatInfo.getChatId(), String.valueOf(memberId));
        }

        RouteDecision decision = chatRoutingService.decide(question);
        log.info("route={} score={} reason={} q={}"
                , decision.route(), decision.score(), decision.reason(), question);


        switch (decision.route()) {
            // RAG로 상담
            case RAG -> {
                return ragService.chat(memberId, question, 4);
            }
            // LLM 채팅으로 상담
            case GENERAL -> {
                return openAIService.generate(memberId, request.getQuestion());
            }
        }
        return null;
    }

    // 스트림
//    @ResponseBody
//    @PostMapping("/chat/stream")
//    public Flux<String> streamChat(@RequestBody Map<String, String> body) {
//        return openAIService.generateStream(body.get("text"));
//    }

    @ResponseBody
    @PostMapping("/chat/history/{chatId}")
    public List<ChatMessage> getChatHistory(@PathVariable("chatId") Long chatId) {
        return chatService.readAllChats(chatId);
    }

    @PostMapping("/admin/rag/upload")
    public ResponseEntity<?> uploadRagDocument(
            @RequestParam("file") MultipartFile file
//            @RequestParam("category") String category // POLICY, FAQ, NOTICE
    ) {
        ragIngestService.ingest(file);
        return ResponseEntity.ok("업로드 완료");
    }

    @ResponseBody
    @GetMapping("/chat/status/{memberId}")
    public Map<String, String> getStatus(@PathVariable Long memberId) {

        return conversationService.findLatestConversation(memberId)
                .map(c -> Map.of("status", c.getStatus().name()))
                .orElse(Map.of("status", "BOT_ACTIVE"));
    }

    @ResponseBody
    @GetMapping("/chat/latest/{memberId}")
    public Map<String, Object> getLatestChat(@PathVariable Long memberId) {
        return conversationService.findLatestConversation(memberId)
                .map(c -> Map.<String, Object>of(
                        "chatId", c.getChatId(),
                        "status", c.getStatus().name()
                ))
                .orElse(Map.<String, Object>of("status", "BOT_ACTIVE"));
    }

    private Long resolveMemberId(CustomOAuth2User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }

        Long memberId = tryExtractMemberId(user);
        if (memberId != null) {
            return memberId;
        }

        String loginId = user.getUsername();
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
        }

        Member member = memberRepository.findByLoginId(loginId);
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
        }

        return member.getMemberId();
    }

    private Long tryExtractMemberId(CustomOAuth2User user) {
        Map<String, Object> attributes = user.getAttributes();
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        Object value = attributes.get("memberId");
        if (value == null) {
            value = attributes.get("member_id");
        }
        if (value == null) {
            value = attributes.get("id");
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        return null;
    }

}
