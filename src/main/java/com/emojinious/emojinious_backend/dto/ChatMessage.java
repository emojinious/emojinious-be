package com.emojinious.emojinious_backend.dto;

import lombok.Data;

@Data
public class ChatMessage {
    private String playerId;
    private String sender;
    private String content;
    private long timestamp;
}