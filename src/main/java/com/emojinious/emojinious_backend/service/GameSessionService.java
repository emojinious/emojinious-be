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
        GameSettings currentSettings = redisUtil.get(gameSettingsKey, GameSettings.class);
        if (currentSettings == null) {
            currentSettings = new GameSettings();
        }

        updateSettingsIfPresent(currentSettings, settings);

        redisUtil.set(gameSettingsKey, currentSettings);

        GameSession gameSession = redisUtil.get("game:session:" + sessionId, GameSession.class);
        if (gameSession != null) {
            gameSession.setSettings(currentSettings);
            redisUtil.set("game:session:" + sessionId, gameSession);
        }
    }

    private void updateSettingsIfPresent(GameSettings currentSettings, GameSettingsDto newSettings) {
        if (newSettings.getPromptTimeLimit() != null) {
            currentSettings.setPromptTimeLimit(newSettings.getPromptTimeLimit());
        }
        if (newSettings.getGuessTimeLimit() != null) {
            currentSettings.setGuessTimeLimit(newSettings.getGuessTimeLimit());
        }
        if (newSettings.getDifficulty() != null) {
            currentSettings.setDifficulty(newSettings.getDifficulty());
        }
        if (newSettings.getTurns() != null) {
            currentSettings.setTurns(newSettings.getTurns());
        }
        if (newSettings.getTheme() != null) {
            currentSettings.setTheme(newSettings.getTheme());
        }
    }
}