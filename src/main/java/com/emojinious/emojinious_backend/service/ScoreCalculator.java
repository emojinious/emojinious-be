package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.dto.PlayerDto;
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
        System.out.println("guess = " + guess + ", target = " + target);
        if (guess.equals(target)) {
            return 100;
        } else {
            return getSimilarityScore(guess, target);
        }
    }

    private float getSimilarityScore(String sentence1, String sentence2) {
//         String url = "http://192.168.0.40:8000/api/similarity/score";
        String url = "http://127.0.0.1:8000/api/similarity/score";
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

            //<나 <상대방, 내가 얻은 점수>>
            Map<String, Float> playerScores = gameSession.getPlayerScores().get(player.getId());
            // <상대방id, 내가 얻은 점수>
            if (playerScores != null) {
                Map<String, Float> scoreMap = new HashMap<>();
                for (Map.Entry<String, Float> entry : playerScores.entrySet()) {
                    scoreMap.put(entry.getKey(), entry.getValue());
                    totalScore += entry.getValue();
                }
                roundScores.add(scoreMap);

                // 다른 플레이어가 나의 문제에 대해 얻은 점수의 평균 계산
                float sumOfOthersScores = 0f;
                int count = 0;
                for (Map.Entry<String, Map<String, Float>> entry : gameSession.getPlayerScores().entrySet()) {
                    if (!entry.getKey().equals(player.getId())) {
                        Map<String, Float> otherPlayerScores = entry.getValue();
                        if (otherPlayerScores.containsKey(player.getId())) {
                            sumOfOthersScores += otherPlayerScores.get(player.getId());
                            count++;
                        }
                    }
                }
//                float averageScore = count > 0 ? sumOfOthersScores / count : 0f;
                float additionalScore = sumOfOthersScores * 0.7f; // 0.7 weight
                scoreMap.put(player.getId(), additionalScore);
                totalScore += additionalScore;
            }

            playerDto.setScore(totalScore);
            playerDto.setRoundScores(roundScores);
            playerDto.setGeneratedImages(gameSession.getGeneratedImages().get(player.getId()));
            playerDto.setCurrentKeywords(gameSession.getCurrentKeywords().get(player.getId()));

//            Map<String, Map<String, String>> currentGuesses = new HashMap<>();
//            for (String guesserId : gameSession.getCurrentGuesses().keySet()) {
//                if (player.getId().equals(gameSession.getGuessTargetForPlayer(guesserId))) {
//                    currentGuesses.put(guesserId, gameSession.getCurrentGuesses().get(guesserId));
//                }
//            }

            Map<String, String> currentGuesses = new HashMap<>();
            for (Map<String, String> guess : gameSession.getCurrentGuesses()) {
                System.out.println("guess = " + guess);
                if (guess.get("targetId").equals(player.getId())) {
                    currentGuesses.put(guess.get("guesserId"), guess.get("guess"));
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
