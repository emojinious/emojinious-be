package com.emojinious.emojinious_backend.dto;

import lombok.Data;

@Data
public class PlayerDto {
    private String id;
    private String nickname;
    private int characterId;
    private boolean isHost;
    private int score;
}