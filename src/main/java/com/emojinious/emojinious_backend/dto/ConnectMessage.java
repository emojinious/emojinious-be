package com.emojinious.emojinious_backend.dto;

import lombok.Data;

@Data
public class ConnectMessage {
    private String playerId;
    private String token;
}