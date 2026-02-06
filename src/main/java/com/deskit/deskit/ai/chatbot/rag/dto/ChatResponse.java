package com.deskit.deskit.ai.chatbot.rag.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private List<String> sources;
    private boolean escalated;

}
