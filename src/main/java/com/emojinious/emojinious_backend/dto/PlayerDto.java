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
    private List<Map<String, Float>> roundScores; // 상대방꺼를 맞춰서 내가얻은 점수
                //추측한 사람
    private Map<String, String> currentGuesses; // 내 그림을 뭐라 추측했는지
    private String currentKeywords;
    private String generatedImages;

}


