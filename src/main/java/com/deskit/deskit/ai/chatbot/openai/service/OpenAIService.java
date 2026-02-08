package com.deskit.deskit.ai.chatbot.openai.service;

import com.deskit.deskit.ai.chatbot.openai.entity.ChatInfo;
import com.deskit.deskit.ai.chatbot.openai.repository.ChatRepository;
import com.deskit.deskit.ai.chatbot.rag.dto.ChatResponse;
import com.deskit.deskit.ai.chatbot.rag.service.ChatSaveService;
import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIException;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseStreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Log4j2
@RequiredArgsConstructor
public class OpenAIService {

    @Value("${spring.ai.openai.chat.options.model}")
    private String chatModel;

    private final OpenAiEmbeddingModel openAiEmbeddingModel;
    private final OpenAIClient openAIClient;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatRepository chatRepository;
    private final ChatSaveService chatSaveService;
    private final ConversationService conversationService;

    private static final int MIN_TEXT_LENGTH = 6;
    private static final String TOO_SHORT_MESSAGE =
            "Please share a little more detail so I can help you accurately.";
    private static final String OPENAI_ERROR_MESSAGE =
            "The assistant is temporarily unavailable. Please try again in a moment.";

    public ChatResponse generate(Long memberId, String text) {
        ChatInfo chatInfo = conversationService.getOrCreateActiveConversation(memberId);

        List<Message> messages = new ArrayList<>();

        messages.add(new SystemMessage(
                """
                        당신은 고객지원을 돕는 AI 상담 챗봇입니다.
                        
                        고객이 문의를 자세히 설명하도록 "~에 대해 알려줘" 같은 형식으로 유도해 주세요.
                        
                        질문 길이가 너무 짧으면(6글자 이하) 아래처럼 안내해 주세요.
                        - 조금 더 구체적으로 상황을 설명해 주세요.
                        - 질문이 이해되지 않으면 다시 한 번 설명해 주세요.
                        
                        
                        """
        ));

        messages.add(new UserMessage(text));

        String answer;
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(chatModel)
                    .inputOfResponse(buildResponseInput(messages))
                    .temperature(0.7)
                    .build();
            Response response = openAIClient.responses().create(params);
            answer = extractOutputText(response);
        } catch (OpenAIException e) {
            log.error("OpenAI chat error", e);
            return new ChatResponse(
                    "현재 상담 시스템에 문제가 발생했어요. 잠시 후 다시 시도해 주세요.",
                    List.of(),
                    false
            );
        }
        chatSaveService.saveChat(chatInfo.getChatId(), text, answer);
        chatSaveService.saveChatMemory(String.valueOf(memberId), text, chatMemoryRepository);

        return new ChatResponse(answer, List.of(), false);
    }

    public Flux<String> generateStream(Long memberId, String text) {
        ChatInfo chatInfo = conversationService.getOrCreateActiveConversation(memberId);

        List<Message> messages = new ArrayList<>();

        messages.add(new SystemMessage(
                """
                        당신은 고객지원을 돕는 AI 상담 챗봇입니다.
                        
                        고객이 문의를 자세히 설명하도록 "~에 대해 알려줘" 같은 형식으로 유도해 주세요.
                        
                        질문 길이가 너무 짧으면(6글자 이하) 아래처럼 안내해 주세요.
                        - 조금 더 구체적으로 상황을 설명해 주세요.
                        - 질문이 이해되지 않으면 다시 한 번 설명해 주세요.
                        
                        
                        """
        ));

        messages.add(new UserMessage(text));

        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(chatModel)
                .inputOfResponse(buildResponseInput(messages))
                .temperature(0.7)
                .build();

        StringBuilder responseBuffer = new StringBuilder();
        AtomicBoolean failed = new AtomicBoolean(false);

        return Flux.using(
                        () -> openAIClient.responses().createStreaming(params),
                        stream -> Flux.fromStream(stream.stream())
                                .filter(ResponseStreamEvent::isOutputTextDelta)
                                .map(event -> event.asOutputTextDelta().delta())
                                .filter(delta -> delta != null && !delta.isEmpty())
                                .doOnNext(responseBuffer::append),
                        StreamResponse::close
                )
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(error -> {
                    failed.set(true);
                    log.error("OpenAI stream error", error);
                })
                .onErrorResume(error -> Flux.just(OPENAI_ERROR_MESSAGE))
                .doOnComplete(() -> {
                    if (failed.get()) {
                        return;
                    }
                    String answer = responseBuffer.toString();
                    chatSaveService.saveChat(chatInfo.getChatId(), text, answer);
                    chatSaveService.saveChatMemory(String.valueOf(memberId), text, chatMemoryRepository);
                });
    }

//    public Flux<String> generateStream(String text) {
//        String normalized = normalize(text);
//        if (isTooShort(normalized)) {
//            return Flux.just(TOO_SHORT_MESSAGE);
//        }
//
//
//        Long chatId = 1L;
//        String memberId = String.valueOf(chatId);
//
//        ChatMessage chatMessage = new ChatMessage();
//        chatMessage.setChatId(chatId);
//        chatMessage.setType(MessageType.USER);
//        chatMessage.setContent(normalized);
//
//        ChatMemory chatMemory = MessageWindowChatMemory.builder()
//                .maxMessages(10)
//                .chatMemoryRepository(chatMemoryRepository)
//                .build();
//
//
//
//        StringBuilder responseBuffer = new StringBuilder();
//
//        return Flux.fromCallable(() ->
//                        responsesClient.createResponse(chatMemory.get(memberId), "gpt-4.1-mini", 0.7))
//                .map(token -> {
//                    responseBuffer.append(token);
//                    return token;
//                })
//                .doOnError(error -> {
//                    if (error instanceof RestClientResponseException responseException) {
//                        log.error("OpenAI stream error status={} body={}",
//                                responseException.getRawStatusCode(),
//                                responseException.getResponseBodyAsString());
//                        return;
//                    }
//                    log.error("OpenAI stream error", error);
//                })
//                .onErrorResume(error -> Flux.just(OPENAI_ERROR_MESSAGE))
//                .doOnComplete(() -> {
//                    chatMemory.add(memberId, new AssistantMessage(responseBuffer.toString()));
//                    chatMemoryRepository.saveAll(memberId, chatMemory.get(memberId));
//
//                    ChatMessage chatAssistantEntity = new ChatMessage();
//                    chatAssistantEntity.setChatId(chatId);
//                    chatAssistantEntity.setType(MessageType.ASSISTANT);
//                    chatAssistantEntity.setContent(responseBuffer.toString());
//
//                    chatRepository.saveAll(List.of(chatMessage, chatAssistantEntity));
//                });
//    }

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

    public List<float[]> generateEmbedding(List<String> texts, String model) {

        EmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model(model).build();

        EmbeddingRequest prompt = new EmbeddingRequest(texts, embeddingOptions);

        EmbeddingResponse response = openAiEmbeddingModel.call(prompt);
        return response.getResults().stream()
                .map(Embedding::getOutput)
                .toList();
    }

    private boolean isTooShort(String text) {
        return text == null || text.trim().length() < MIN_TEXT_LENGTH;
    }

    private ChatResponse buildShortResponse(String text) {
        if (text == null || text.isBlank()) {
            return ChatResponse.builder()
                    .answer("Please enter a question.")
                    .sources(List.of())
                    .escalated(false)
                    .build();
        }
        return ChatResponse.builder()
                .answer(TOO_SHORT_MESSAGE)
                .sources(List.of())
                .escalated(false)
                .build();
    }

    private ChatResponse buildErrorResponse() {
        return ChatResponse.builder()
                .answer(OPENAI_ERROR_MESSAGE)
                .sources(List.of())
                .escalated(false)
                .build();
    }

    private String normalize(String text) {
        return text == null ? null : text.trim();
    }
}
