package com.emojinious.emojinious_backend.dto;

import lombok.Data;

@Data
public class JoinGameMessage {
    private String playerId;
    private String nickname;
    private int characterId;
}