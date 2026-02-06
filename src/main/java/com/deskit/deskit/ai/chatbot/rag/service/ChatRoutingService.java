package com.deskit.deskit.ai.chatbot.rag.service;

import com.deskit.deskit.ai.chatbot.rag.entity.ChatRoute;
import com.deskit.deskit.ai.chatbot.rag.entity.RouteDecision;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
public class ChatRoutingService {

    private final RedisVectorStore vectorStore;

    @Value("${rag.routing.top-k:1}")
    private int routingTopK;

    @Value("${rag.routing.similarity-threshold:0.78}")
    private double similarityThreshold;

    @Value("${rag.routing.min-length:6}")
    private int minLenForRouting;

    public ChatRoutingService(RedisVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public RouteDecision decide(String question) {
        String q = normalize(question);
        log.info(q);

        if (q == null || q.isBlank()) {
            log.info("q is blank");
            return new RouteDecision(ChatRoute.GENERAL, 0, "empty");
        }

        if (q.length() < minLenForRouting) {
            log.info("q is too short");
            return new RouteDecision(ChatRoute.GENERAL, 0, "too_short");
        }

        SearchRequest request = SearchRequest.builder()
                .query(q)
                .topK(routingTopK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);

        if (documents.isEmpty()) {
            return new RouteDecision(ChatRoute.GENERAL, 0, "low_similarity");
        }
        return new RouteDecision(ChatRoute.RAG, 1, "vector_similarity");
    }

    private String normalize(String q) {
        if (q == null) {
            return null;
        }
        return q.trim();
    }
}
