package com.emojinious.emojinious_backend.dto;

import lombok.Data;

import java.util.*;

@Data
public class PlayerScoreDto {
    private List<Map<String, Float>> roundScores;

    public PlayerScoreDto() {
        this.roundScores = new ArrayList<>(); // 리스트 초기화
    }

    public void addScore(String playerId, float score) {
        Map<String, Float> scoreEntry = new HashMap<>();
        scoreEntry.put(playerId, score);
        this.roundScores.add(scoreEntry);
    }
}
