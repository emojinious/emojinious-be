package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.model.GameSession;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ScoreCalculator {
    public Map<String, Integer> calculateScores(GameSession gameSession) {
        Map<String, Integer> scores = new HashMap<>();

        for (String playerId : gameSession.getCurrentKeywords().keySet()) {
            int score = 0;
            String originalKeyword = gameSession.getCurrentKeywords().get(playerId);

            //
            for (String guesserId : gameSession.getCurrentGuesses().keySet()) {
                if (!guesserId.equals(playerId) && gameSession.getCurrentGuesses().get(guesserId).equals(originalKeyword)) {
                    score += 10;
                }
            }

            //
            String playerGuess = gameSession.getCurrentGuesses().get(playerId);
            String guessTarget = getGuessTargetForPlayer(gameSession, playerId);
            if (playerGuess != null && playerGuess.equals(guessTarget)) {
                score += 15;
            }

            scores.put(playerId, score);
        }

        return scores;
    }

    private String getGuessTargetForPlayer(GameSession gameSession, String playerId) {
        // 플레이어가 추측해야 했던 키워드를 반환
        int playerIndex = gameSession.getPlayers().indexOf(gameSession.getPlayerById(playerId));
        int targetIndex = (playerIndex + 1) % gameSession.getPlayers().size();
        String targetPlayerId = gameSession.getPlayers().get(targetIndex).getId();
        return gameSession.getCurrentKeywords().get(targetPlayerId);
    }
}