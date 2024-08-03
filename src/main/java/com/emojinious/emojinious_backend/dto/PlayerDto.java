package com.emojinious.emojinious_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PlayerDto {
    private String id;
    private String nickname;
    private int characterId;
    @JsonProperty("isHost")
    private boolean isHost;
    private int score;
}