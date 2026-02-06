package com.deskit.deskit.directchat.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DirectChatMessageRequest {

    private String sender;
    private String content;
}
