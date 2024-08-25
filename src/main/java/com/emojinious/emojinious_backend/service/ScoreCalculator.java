package com.emojinious.emojinious_backend.service;

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

    public TurnResultDto calculateScores(GameSession gameSession) {
        Map<String, PlayerScoreDto> scores = new HashMap<>();
        Map<String, PlayerDto> playerResults = new HashMap<>();
        Map<String, Float> correctGuessesSum = new HashMap<>();
        Map<String, Integer> guessCount = new HashMap<>();

        for (String playerId : gameSession.getCurrentKeywords().keySet()) {
            scores.put(playerId, new PlayerScoreDto());
            correctGuessesSum.put(playerId, 0f);
            guessCount.put(playerId, 0);
        }

        for (String guesserId : gameSession.getCurrentGuesses().keySet()) {
            String playerGuess = gameSession.getCurrentGuesses().get(guesserId);
            String guessTarget = getGuessTargetForPlayer(gameSession, guesserId);
            String targetPlayerId = getTargetPlayerId(gameSession, guesserId);

            int currentScore;
            if (playerGuess.equals(guessTarget)) {
                // 정확히 맞춘 경우
                currentScore = 100;
            } else {
                // 유사도 측정
                float similarityScore = getSimilarityScore(playerGuess, guessTarget);
                currentScore = (int) Math.round(similarityScore); // 0~1 실수값을 0~100 정수로 변환
            }

            scores.get(targetPlayerId).addScore(guesserId, currentScore);
            correctGuessesSum.put(targetPlayerId, correctGuessesSum.get(targetPlayerId) + currentScore);
            guessCount.put(targetPlayerId, guessCount.get(targetPlayerId) + 1);
        }
//        score
//        플레이어1 : [플레이어2, 20], [플레이어3, 30], []
//        플레이어2 : [플레이어1, 15], [플레이어3, 20]
//        플레이어3 : [플레이어1, 20], [플레이어2, 20]



        // 키워드 주인에게 추가 점수 부여
        for (String playerId : gameSession.getCurrentKeywords().keySet()) {
            float averageGuessScore;
            float totalGuessScore = correctGuessesSum.get(playerId);
            int playerGuessCount = guessCount.get(playerId);
            if (playerGuessCount > 0) {
                averageGuessScore = totalGuessScore / playerGuessCount;
            } else {
                averageGuessScore = 0;
            }

            scores.get(playerId).addScore(playerId, averageGuessScore);

            // PlayerDto 객체 생성 및 설정
            PlayerDto playerDto = new PlayerDto();
            playerDto.setId(playerId);
            playerDto.setNickname(gameSession.getPlayerById(playerId).getNickname());
            playerDto.setCharacterId(gameSession.getPlayerById(playerId).getCharacterId());
            playerDto.setHost(gameSession.getPlayerById(playerId).isHost());

            playerDto.setScore(calculateTotalScore(playerId, scores));
            playerDto.setRoundScores(scores.get(playerId).getRoundScores());
            playerDto.setGeneratedImages(gameSession.getGeneratedImages().get(playerId));
            playerDto.setCurrentKeywords(gameSession.getCurrentKeywords().get(playerId));


            Map<String, String> currentGuesses = new HashMap<>();
            for (String guesserId : gameSession.getCurrentGuesses().keySet()) {
                if (playerId.equals(getTargetPlayerId(gameSession, guesserId))) {
                    System.out.println("Setting current guess for player " + playerId + " from guesser " + guesserId + ": " + gameSession.getCurrentGuesses().get(guesserId));
                    currentGuesses.put(guesserId, gameSession.getCurrentGuesses().get(guesserId));
                }
            }
            playerDto.setCurrentGuesses(currentGuesses);

            playerResults.put(playerId, playerDto);
        }
        TurnResultDto turnResultDto = new TurnResultDto();
        turnResultDto.setTurnResult(playerResults);

        return turnResultDto;
    }

    private float calculateTotalScore(String playerId, Map<String, PlayerScoreDto> scores) {
        float total = 0f;

        // 모든 플레이어의 PlayerScoreDto를 순회
        for (PlayerScoreDto playerScoreDto : scores.values()) {
            // 각 PlayerScoreDto의 roundScores를 확인
            for (Map<String, Float> roundScore : playerScoreDto.getRoundScores()) {
                // roundScores에서 해당 playerId에 대한 점수를 합산
                if (roundScore.containsKey(playerId)) {
                    total += roundScore.get(playerId);
                }
            }
        }

        return total;
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
            return 0f; // 에러 발생 시 0점 처리
        }
    }
}