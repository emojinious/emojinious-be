package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.dto.PlayerDto;
import com.emojinious.emojinious_backend.dto.PlayerScoreDto;
import com.emojinious.emojinious_backend.dto.TurnResultDto;
import com.emojinious.emojinious_backend.model.GameSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class ScoreCalculator {

    @Autowired
    private RestTemplate restTemplate;

    public float calculateSingleGuessScore(String guess, String target) {
        if (guess.equals(target)) {
            return 100;
        } else {
            return getSimilarityScore(guess, target) * 100;
        }
    }

    private float getSimilarityScore(String sentence1, String sentence2) {
        String url = "http://yhcho.ddns.net:8000/api/similarity/score";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("sentence1", sentence1);
        body.put("sentence2", sentence2);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            Map<String, Double> response = restTemplate.postForObject(url, request, Map.class);
            return response.get("result").floatValue();
        } catch (Exception e) {
            e.printStackTrace();
            return 0f;
        }
    }

    public TurnResultDto calculateFinalScores(GameSession gameSession) {
        Map<String, PlayerDto> playerResults = new HashMap<>();

        for (Player player : gameSession.getPlayers()) {
            PlayerDto playerDto = new PlayerDto();
            playerDto.setId(player.getId());
            playerDto.setNickname(player.getNickname());
            playerDto.setCharacterId(player.getCharacterId());
            playerDto.setHost(player.isHost());

            float totalScore = 0f;
            List<Map<String, Float>> roundScores = new ArrayList<>();

            Map<String, Float> playerScores = gameSession.getPlayerScores().get(player.getId());
            if (playerScores != null) {
                Map<String, Float> scoreMap = new HashMap<>();
                for (Map.Entry<String, Float> entry : playerScores.entrySet()) {
                    scoreMap.put(entry.getKey(), entry.getValue());
                    totalScore += entry.getValue();
                }
                roundScores.add(scoreMap);
            }

            playerDto.setScore(totalScore);
            playerDto.setRoundScores(roundScores);
            playerDto.setGeneratedImages(gameSession.getGeneratedImages().get(player.getId()));
            playerDto.setCurrentKeywords(gameSession.getCurrentKeywords().get(player.getId()));

            Map<String, String> currentGuesses = new HashMap<>();
            for (String guesserId : gameSession.getCurrentGuesses().keySet()) {
                if (player.getId().equals(gameSession.getGuessTargetForPlayer(guesserId))) {
                    currentGuesses.put(guesserId, gameSession.getCurrentGuesses().get(guesserId));
                }
            }
            playerDto.setCurrentGuesses(currentGuesses);

            playerResults.put(player.getId(), playerDto);
        }

        TurnResultDto turnResultDto = new TurnResultDto();
        turnResultDto.setTurnResult(playerResults);

        return turnResultDto;
    }


}