package com.deskit.deskit.ai.chatbot.rag.service;

import com.deskit.deskit.ai.config.RagVectorProperties;
import com.deskit.deskit.ai.chatbot.openai.entity.ChatInfo;
import com.deskit.deskit.ai.chatbot.openai.service.OpenAIService;
import com.deskit.deskit.ai.chatbot.openai.service.ConversationService;
import com.deskit.deskit.ai.chatbot.rag.dto.ChatResponse;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class RagService {

    @Value("${spring.ai.openai.chat.options.model}")
    private String chatModel;
    private static final double TEMPERATURE = 0.7;
    private static final String NO_CONTEXT_MESSAGE =
            "해당 내용은 현재 제공된 정보로는 안내드릴 수 없습니다. \"관리자 연결\"을 입력하시면 관리자에게 문의가 접수됩니다.";

    private final RedisVectorStore vectorStore;
    private final RagVectorProperties properties;
    private final AdminEscalationService adminEscalationService;
    private final ConversationService conversationService;
    private final ChatSaveService chatSaveService;
    private final ChatMemoryRepository chatMemoryRepository;
    private final OpenAIClient openAIClient;
    private final OpenAIService openAIService;

    private static final String ESCALATION_TRIGGER = "관리자 연결";

    public ChatResponse chat(Long memberId, String question, int topK) {
        ChatInfo chatInfo = conversationService.getOrCreateActiveConversation(memberId);

        boolean escalated = false;

        String normalized = normalize(question);
        if (normalized != null && normalized.equals(ESCALATION_TRIGGER)) {
            log.info("Escalation triggered");
            return adminEscalationService.escalate(normalized, chatInfo.getChatId(), String.valueOf(memberId));
        }

        int candidates = topK > 0 ? topK : properties.getTopK();
        log.info("[RAG] chat() called. question={}", normalized);

        if (normalized == null || normalized.isBlank()) {
            return new ChatResponse(NO_CONTEXT_MESSAGE, List.of(), false);
        }

        SearchRequest searchRequest = SearchRequest.builder()
                .query(normalized)
                .topK(candidates)
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        log.info("[RAG] similaritySearch result size={}", documents.size());

        if (documents.isEmpty()) {
            return openAIService.generate(memberId, question);
        }

        String context = buildContext(documents);
        if (context.isBlank()) {
            return openAIService.generate(memberId, question);
        }

        List<Message> messages = buildMessages(context, normalized);

        String answer;
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(chatModel)
                    .inputOfResponse(buildResponseInput(messages))
                    .temperature(TEMPERATURE)
                    .build();
            Response response = openAIClient.responses().create(params);
            answer = extractOutputText(response);
        } catch (OpenAIException e) {
            log.error("RAG OpenAI error", e);
            return new ChatResponse(
                    "현재 상담 시스템에 문제가 발생했습니다. 잠시 후 다시 시도해주세요.",
                    List.of(),
                    false
            );
        }
        if (answer == null || answer.isBlank()) {
            answer = NO_CONTEXT_MESSAGE;
        }
        chatSaveService.saveChat(chatInfo.getChatId(), question, answer);
        chatSaveService.saveChatMemory(String.valueOf(memberId), question, chatMemoryRepository);

        List<String> sources = documents.stream()
                .map(doc -> String.valueOf(doc.getMetadata().getOrDefault("source", "")))
                .toList();

        return new ChatResponse(answer, sources, escalated);
    }

    private String buildContext(List<Document> documents) {
        return documents.stream()
                .limit(3)
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"))
                .trim();
    }

    private List<Message> buildMessages(String context, String question) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(
                """
                        당신은 고객 지원 AI 어시스턴트입니다.

                        제공된 문맥을 사용해 사용자의 질문에 답변하세요.
                        문맥이 관련 있으나 불완전하다면, 알고 있는 범위에서 답변하고
                        짧은 추가 질문을 하세요.
                        문맥에 충분한 정보가 없다면, 제공된 문서에서 답을 찾지 못했다고
                        말하고 더 자세한 정보를 요청하세요.

                        사용자가 관리자와 연결을 원하는 것 같다고 판단되면, 아래와 같이 대답하세요.
                        "관리자와 상담을 원하시는 것 같아요. '관리자 연결'이라고 입력하면 관리자와 상담이 가능해요."

                        문맥에 근거하지 않은 사실을 만들어내지 마세요.
                        응답은 간결하고 도움이 되도록 작성하세요.
                        마크다운 형식으로 답변해 주세요. 필요에 따라 제목, 목록, 코드 구분 기호를 사용하세요.
                        """
        ));
        messages.add(new UserMessage("""
                [Context]
                %s

                [Question]
                %s
                """.formatted(context, question)));
        return messages;
    }

    private String normalize(String question) {
        return question == null ? null : question.trim();
    }

    private List<ResponseInputItem> buildResponseInput(List<Message> messages) {
        List<ResponseInputItem> inputItems = new ArrayList<>();
        for (Message message : messages) {
            EasyInputMessage easyMessage = EasyInputMessage.builder()
                    .role(toEasyInputRole(message))
                    .content(getMessageText(message))
                    .build();
            inputItems.add(ResponseInputItem.ofEasyInputMessage(easyMessage));
        }
        return inputItems;
    }

    private EasyInputMessage.Role toEasyInputRole(Message message) {
        return switch (message.getMessageType()) {
            case SYSTEM -> EasyInputMessage.Role.SYSTEM;
            case USER -> EasyInputMessage.Role.USER;
            case ASSISTANT -> EasyInputMessage.Role.ASSISTANT;
            case TOOL -> EasyInputMessage.Role.SYSTEM;
        };
    }

    private String getMessageText(Message message) {
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.getText();
        }
        if (message instanceof UserMessage userMessage) {
            return userMessage.getText();
        }
        if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getText();
        }
        return "";
    }

    private String extractOutputText(Response response) {
        if (response == null || response.output() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }
            for (ResponseOutputMessage.Content content : item.asMessage().content()) {
                if (content.isOutputText()) {
                    text.append(content.asOutputText().text());
                }
            }
        }
        return text.toString();
    }

    public void ingest(List<Document> documents) {
        vectorStore.add(documents);
        log.info("vectorStore added: " + documents);
    }

    public Document createDocument(String content, Map<String, Object> metadata) {
        return new Document(content, metadata);
    }
}
