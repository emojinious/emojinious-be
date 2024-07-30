package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.PlayerSessionCache;
import com.emojinious.emojinious_backend.dto.GameSettingsDto;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameSessionService {
    private final RedisTemplate<String, Object> redisTemplate;

    public void updateGameSettings(String sessionId, String playerId, GameSettingsDto settings) {
        PlayerSessionCache player = (PlayerSessionCache) redisTemplate.opsForValue().get("player:" + playerId);
        if (player == null || !player.getSessionId().equals(sessionId) || !player.isHost()) {
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