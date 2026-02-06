package com.deskit.deskit.ai.chatbot.rag.entity;

public record RouteDecision(ChatRoute route, int score, String reason) {
}
