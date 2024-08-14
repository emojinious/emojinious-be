package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.dto.GameSettingsDto;
import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.model.GameSession;
import com.emojinious.emojinious_backend.model.GameSettings;
import com.emojinious.emojinious_backend.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameSessionService {
    private final RedisUtil redisUtil;

    public void updateGameSettings(String sessionId, String playerId, GameSettingsDto settings) {
        Player player = redisUtil.get("player:" + playerId, Player.class);
        if (player == null) {
            throw new IllegalArgumentException("Player not found");
        }

        if (!player.getSessionId().equals(sessionId) || !player.isHost()) {
            throw new IllegalArgumentException("Invalid player or not authorized to change settings");
        }

        String gameSettingsKey = "game:settings:" + sessionId;
        redisUtil.set(gameSettingsKey, settings);

        GameSession gameSession = redisUtil.get("game:session:" + sessionId, GameSession.class);
        if (gameSession != null) {
            GameSettings gameSettings = gameSession.getSettings();
            gameSettings.setPromptTimeLimit(settings.getPromptTimeLimit());
            gameSettings.setGuessTimeLimit(settings.getGuessTimeLimit());
            gameSettings.setDifficulty(settings.getDifficulty());
            gameSettings.setTurns(settings.getTurns());
            redisUtil.set("game:session:" + sessionId, gameSession);
        }
    }
}