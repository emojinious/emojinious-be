package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.PlayerSessionCache;
import com.emojinious.emojinious_backend.dto.GameSettingsDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


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

        PlayerSessionCache player;
        if (playerData instanceof PlayerSessionCache) {
            player = (PlayerSessionCache) playerData;
        } else if (playerData instanceof Map) {
            player = objectMapper.convertValue(playerData, PlayerSessionCache.class);
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
                "turns", settings.getTurns()
        ));
    }
}