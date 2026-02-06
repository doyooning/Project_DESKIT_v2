package com.deskit.deskit.ai.evaluate.service;

import com.deskit.deskit.account.entity.SellerRegister;
import com.deskit.deskit.ai.config.RagVectorProperties;
import com.deskit.deskit.ai.evaluate.dto.EvaluateDTO;
import com.deskit.deskit.ai.evaluate.entity.AiEvaluation;
import com.deskit.deskit.ai.evaluate.repository.AiEvalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class SellerPlanEvaluationService {

    @Value("${spring.ai.openai.chat.options.model}")
    private String chatModel;

    @Qualifier("evalVectorStore")
    private final RedisVectorStore vectorStore;
    private final OpenAIClient openAIClient;
    private final RagVectorProperties ragVectorProperties;
    private final AiTools chatTools;
    private final AiEvalRepository aiEvalRepository;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public AiEvaluation evaluateAndSave(SellerRegister registerEntity) {
        if (registerEntity == null) {
            throw new IllegalArgumentException("seller register is required");
        }
        if (registerEntity.getPlanFile() == null || registerEntity.getPlanFile().length == 0) {
            throw new IllegalArgumentException("business plan file is required");
        }

        String planText = extractPlanText(registerEntity.getPlanFile());
        String policyContext = buildPolicyContext(planText);

        SystemMessage systemMessage = new SystemMessage("""
                당신은 DESKIT 플랫폼 판매자 회원가입 심사를 담당하는 AI입니다.
                제공된 사업계획서와 정책 문서(Context)를 바탕으로만 평가하세요.
                문서 근거가 부족하면 요약에 그 이유를 명시하고, 추측은 하지 마세요.

                점수는 0~20 범위로 작성하고, total_score는 항목 합계로 작성하세요.
                gradeRecommended는 SellerGrade 열거형 값 중 하나로 지정하세요.
                
                요약에는 단순히 결과를 요약만 하지 말고, 판매자에게 해당 점수를 부여한 이유를 알려주세요.

                반드시 getEvaluateResultTool 함수를 호출해서 결과를 반환하세요.
                응답은 아래 JSON 형식으로만 출력하세요.
                {
                  "businessStability": 0,
                  "productCompetency": 0,
                  "liveSuitability": 0,
                  "operationCoop": 0,
                  "growthPotential": 0,
                  "totalScore": 0,
                  "gradeRecommended": "A|B|C|REJECTED",
                  "summary": "..."
                }
                """);

        UserMessage userMessage = new UserMessage("""
                [판매자 정보]
                회사명: %s
                설명: %s

                [사업계획서]
                %s

                [정책 문서 발췌]
                %s
                """.formatted(
                nullSafe(registerEntity.getCompanyName()),
                nullSafe(registerEntity.getDescription()),
                planText,
                policyContext
        ));
        log.info(userMessage);
        EvaluateDTO evaluateDTO = requestEvaluation(systemMessage, userMessage);
        log.info(evaluateDTO);

        AiEvaluation evaluation = toEntity(evaluateDTO, registerEntity);

        return aiEvalRepository.save(evaluation);
    }

    private AiEvaluation toEntity(EvaluateDTO evaluateDTO, SellerRegister registerEntity) {
        AiEvaluation evaluation = new AiEvaluation();
        evaluation.setBusinessStability(evaluateDTO.businessStability());
        evaluation.setProductCompetency(evaluateDTO.productCompetency());
        evaluation.setLiveSuitability(evaluateDTO.liveSuitability());
        evaluation.setOperationCoop(evaluateDTO.operationCoop());
        evaluation.setGrowthPotential(evaluateDTO.growthPotential());
        evaluation.setTotalScore(evaluateDTO.total_score());
        evaluation.setSellerGrade(evaluateDTO.gradeRecommended());
        evaluation.setSummary(evaluateDTO.summary());
        evaluation.setSellerId(registerEntity.getSellerId());
        evaluation.setRegisterId(registerEntity.getRegisterId());
        return evaluation;
    }

    private String extractPlanText(byte[] planFile) {
        InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(planFile));
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();
        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    private String buildPolicyContext(String planText) {
        int topK = ragVectorProperties.getTopK() > 0 ? ragVectorProperties.getTopK() : 4;
        String query = planText.isBlank() ? "판매자 사업계획서 심사 기준" : trimQuery(planText);
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK * 3)
                .build();
        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        List<Document> filteredDocuments = documents.stream()
                .filter(doc -> {
                    String rawSource = String.valueOf(doc.getMetadata().getOrDefault("source", ""));
                    String normalized = rawSource == null ? "" : rawSource.trim().toLowerCase();
                    return normalized.equals("policy_v2.pdf") || normalized.endsWith("/policy_v2.pdf") || normalized.endsWith("\\policy_v2.pdf");
                })
                .limit(topK)
                .toList();
        if (filteredDocuments.isEmpty() && !documents.isEmpty()) {
            String sources = documents.stream()
                    .map(doc -> String.valueOf(doc.getMetadata().getOrDefault("source", "")))
                    .distinct()
                    .collect(Collectors.joining(", "));
            log.warn("No policy_v2.pdf documents found in search results. Available sources: {}", sources);
        }
        log.info("Document size (filtered): {}", filteredDocuments.size());
        if (!filteredDocuments.isEmpty()) {
            return filteredDocuments.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));
        }

        String fallback = loadPolicyFallback();
        if (!fallback.isBlank()) {
            log.info("Loaded policy context from classpath fallback.");
        }
        return fallback;
    }

    private String trimQuery(String planText) {
        int limit = 2000;
        return planText.length() > limit ? planText.substring(0, limit) : planText;
    }

    private String loadPolicyFallback() {
        Resource resource = resourceLoader.getResource("classpath:rag/eval-seed/policy_v2.pdf");
        if (!resource.exists()) {
            log.warn("Fallback policy resource not found: {}", resource);
            return "";
        }
        try {
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();
            return documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"))
                    .trim();
        } catch (Exception ex) {
            log.warn("Failed to read fallback policy resource", ex);
            return "";
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private EvaluateDTO requestEvaluation(SystemMessage systemMessage, UserMessage userMessage) {
        String answer;
        try {
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(chatModel)
                    .inputOfResponse(buildResponseInput(List.of(systemMessage, userMessage)))
                    .temperature(0.2)
                    .build();
            Response response = openAIClient.responses().create(params);
            answer = extractOutputText(response);
        } catch (OpenAIException e) {
            throw new IllegalStateException("ai evaluation failed", e);
        }

        String payload = normalizeJson(answer);
        return toEvaluateDTO(payload);
    }

    private EvaluateDTO toEvaluateDTO(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            int businessStability = node.path("businessStability").asInt();
            int productCompetency = node.path("productCompetency").asInt();
            int liveSuitability = node.path("liveSuitability").asInt();
            int operationCoop = node.path("operationCoop").asInt();
            int growthPotential = node.path("growthPotential").asInt();
            int totalScore = node.path("totalScore").asInt();
            String grade = node.path("gradeRecommended").asText();
            String summary = node.path("summary").asText("");

            return chatTools.getEvaluateResultTool(
                    businessStability,
                    productCompetency,
                    liveSuitability,
                    operationCoop,
                    growthPotential,
                    totalScore,
                    com.deskit.deskit.account.enums.SellerGradeEnum.valueOf(grade),
                    summary
            );
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("invalid evaluation payload", ex);
        }
    }

    private List<ResponseInputItem> buildResponseInput(List<org.springframework.ai.chat.messages.Message> messages) {
        List<ResponseInputItem> inputItems = new ArrayList<>();
        for (org.springframework.ai.chat.messages.Message message : messages) {
            EasyInputMessage easyMessage = EasyInputMessage.builder()
                    .role(toEasyInputRole(message))
                    .content(getMessageText(message))
                    .build();
            inputItems.add(ResponseInputItem.ofEasyInputMessage(easyMessage));
        }
        return inputItems;
    }

    private EasyInputMessage.Role toEasyInputRole(org.springframework.ai.chat.messages.Message message) {
        return switch (message.getMessageType()) {
            case SYSTEM -> EasyInputMessage.Role.SYSTEM;
            case USER -> EasyInputMessage.Role.USER;
            case ASSISTANT -> EasyInputMessage.Role.ASSISTANT;
            case TOOL -> EasyInputMessage.Role.SYSTEM;
        };
    }

    private String getMessageText(org.springframework.ai.chat.messages.Message message) {
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.getText();
        }
        if (message instanceof UserMessage userMessage) {
            return userMessage.getText();
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

    private String normalizeJson(String answer) {
        if (answer == null) {
            return "";
        }
        String trimmed = answer.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
    }
}
