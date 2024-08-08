package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.dto.GameSettingsDto;
import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.model.GameSession;
import com.emojinious.emojinious_backend.model.GameSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class GameSessionService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void updateGameSettings(String sessionId, String playerId, GameSettingsDto settings) {
        Object playerData = redisTemplate.opsForValue().get("player:" + playerId);
        if (playerData == null) {
            throw new IllegalArgumentException("Player not found");
        }

        Player player;
        if (playerData instanceof Player) {
            player = (Player) playerData;
        } else if (playerData instanceof Map) {
            player = objectMapper.convertValue(playerData, Player.class);
        } else {
            throw new IllegalArgumentException("Invalid player data format");
        }

        if (!player.getSessionId().equals(sessionId) || !player.isHost()) {
            throw new IllegalArgumentException("Invalid player or not authorized to change settings");
        }

        String gameSettingsKey = "game:settings:" + sessionId;
        redisTemplate.opsForHash().putAll(gameSettingsKey, Map.of(
                "promptTimeLimit", settings.getPromptTimeLimit(),
                "guessTimeLimit", settings.getGuessTimeLimit(),
                "difficulty", settings.getDifficulty(),
                "turns", settings.getTurns(),
                "theme", settings.getTheme()
        ));

        GameSession gameSession = (GameSession) redisTemplate.opsForValue().get("game:session:" + sessionId);
        if (gameSession != null) {
            GameSettings gameSettings = gameSession.getSettings();
            gameSettings.setPromptTimeLimit(settings.getPromptTimeLimit());
            gameSettings.setGuessTimeLimit(settings.getGuessTimeLimit());
            gameSettings.setDifficulty(settings.getDifficulty());
            gameSettings.setTurns(settings.getTurns());
            gameSettings.setTheme(settings.getTheme());
            redisTemplate.opsForValue().set("game:session:" + sessionId, gameSession);
        }
    }
}