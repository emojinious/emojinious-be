package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.model.GameSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class ScoreCalculator {

    @Autowired
    private RestTemplate restTemplate;

    public Map<String, Integer> calculateScores(GameSession gameSession) {
        Map<String, Integer> scores = new HashMap<>();
        Map<String, Float> correctGuessesSum = new HashMap<>();
        Map<String, Integer> guessCount = new HashMap<>();

        for (String playerId : gameSession.getCurrentKeywords().keySet()) {
            scores.put(playerId, 0);
            correctGuessesSum.put(playerId, 0f);
            guessCount.put(playerId, 0);
        }

        for (String guesserId : gameSession.getCurrentGuesses().keySet()) {
            String playerGuess = gameSession.getCurrentGuesses().get(guesserId);
            String guessTarget = getGuessTargetForPlayer(gameSession, guesserId);
            String targetPlayerId = getTargetPlayerId(gameSession, guesserId);

            if (playerGuess.equals(guessTarget)) {
                // 정확히 맞춘 경우
                scores.put(guesserId, scores.get(guesserId) + 100);
                correctGuessesSum.put(targetPlayerId, correctGuessesSum.get(targetPlayerId) + 100);
            } else {
                // 유사도 측정
                float similarityScore = getSimilarityScore(playerGuess, guessTarget);
                int roundedScore = Math.round(similarityScore * 100); // 0~1 실수값을 0~100 정수로 변환
                scores.put(guesserId, scores.get(guesserId) + roundedScore);
                correctGuessesSum.put(targetPlayerId, correctGuessesSum.get(targetPlayerId) + similarityScore);
            }
            guessCount.put(targetPlayerId, guessCount.get(targetPlayerId) + 1);
        }

        // 키워드 주인에게 추가 점수 부여
        for (String playerId : gameSession.getCurrentKeywords().keySet()) {
            float totalGuessScore = correctGuessesSum.get(playerId);
            int playerGuessCount = guessCount.get(playerId);
            if (playerGuessCount > 0) {
                float averageGuessScore = totalGuessScore / playerGuessCount;
                int roundedAverageScore = Math.round(averageGuessScore * 100); // 0~1 실수값을 0~100 정수로 변환
                scores.put(playerId, scores.get(playerId) + roundedAverageScore);
            }
        }

        return scores;
    }

    private String getGuessTargetForPlayer(GameSession gameSession, String playerId) {
        String targetPlayerId = getTargetPlayerId(gameSession, playerId);
        return gameSession.getCurrentKeywords().get(targetPlayerId);
    }

    private String getTargetPlayerId(GameSession gameSession, String playerId) {
        int playerIndex = gameSession.getPlayers().indexOf(gameSession.getPlayerById(playerId));
        int targetIndex = (playerIndex + 1) % gameSession.getPlayers().size();
        return gameSession.getPlayers().get(targetIndex).getId();
    }

    private float getSimilarityScore(String sentence1, String sentence2) {
        String url = "http://localhost:8000/api/similarity/score";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("sentence1", sentence1);
        body.put("sentence2", sentence2);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            Map<String, Float> response = restTemplate.postForObject(url, request, Map.class);
            return response.get("result");
        } catch (Exception e) {
            e.printStackTrace();
            return 0f; // 에러 발생 시 0점 처리
        }
    }
}