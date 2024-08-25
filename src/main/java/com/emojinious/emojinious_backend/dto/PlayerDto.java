package com.emojinious.emojinious_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PlayerDto {
    private String id;
    private String nickname;
    private int characterId;
    @JsonProperty("isHost")
    private boolean isHost;

    private float score;
    private List<Map<String, Float>> roundScores;

    private Map<String, String> currentGuesses;
    private String currentKeywords;
    private String generatedImages;

}


