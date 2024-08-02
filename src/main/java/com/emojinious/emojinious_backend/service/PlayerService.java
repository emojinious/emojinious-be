package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.model.Player;
import com.emojinious.emojinious_backend.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private static final String PLAYER_KEY = "player:";
    private static final String SESSION_PLAYER_KEY = "session:player:";
    private static final long PLAYER_EXPIRATION = 3 * 60 * 60; // 3 hours

    public Player createPlayer(String nickname, int characterId, String sessionId, boolean isHost) {
        String playerId = UUID.randomUUID().toString();
        Player player = new Player(playerId, nickname, characterId, isHost);
        player.setSessionId(sessionId);
        String token = jwtUtil.generateToken(playerId, sessionId, isHost);
        player.setToken(token);
        savePlayer(player);
        return player;
    }

    public void savePlayer(Player player) {
        String playerKey = PLAYER_KEY + player.getId();
        String sessionPlayerKey = SESSION_PLAYER_KEY + player.getSessionId();

        redisTemplate.opsForValue().set(playerKey, player, PLAYER_EXPIRATION, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(sessionPlayerKey, player.getId(), PLAYER_EXPIRATION, TimeUnit.SECONDS);
    }

    public Player getPlayerById(String playerId) {
        Object playerData = redisTemplate.opsForValue().get(PLAYER_KEY + playerId);
        if (playerData == null) {
            return null;
        }
        try {
            if (playerData instanceof Player) {
                return (Player) playerData;
            }
            return objectMapper.convertValue(playerData, Player.class);
        } catch (Exception e) {
            log.error("Error deserializing player data for id: " + playerId, e);
            return null;
        }
    }
    public Player getPlayerBySessionId(String sessionId) {
        String playerId = (String) redisTemplate.opsForValue().get(SESSION_PLAYER_KEY + sessionId);
        if (playerId != null) {
            return getPlayerById(playerId);
        }
        return null;
    }

    public void removePlayer(String playerId) {
        Player player = getPlayerById(playerId);
        if (player != null) {
            redisTemplate.delete(PLAYER_KEY + playerId);
            redisTemplate.delete(SESSION_PLAYER_KEY + player.getSessionId());
        }
    }

    public void updatePlayerScore(String playerId, int scoreToAdd) {
        Player player = getPlayerById(playerId);
        if (player != null) {
            player.setScore(player.getScore() + scoreToAdd);
            savePlayer(player);
        }
    }

    public boolean existsBySessionId(String sessionId) {
        return redisTemplate.hasKey(SESSION_PLAYER_KEY + sessionId);
    }

    public String refreshToken(String playerId) {
        Player player = getPlayerById(playerId);
        if (player != null) {
            String newToken = jwtUtil.refreshToken(player.getToken());
            player.setToken(newToken);
            savePlayer(player);
            return newToken;
        }
        return null;
    }

    public String generateInviteLink(String sessionId) {
        return "localhost:8080/?sessionId=" + sessionId;
    }
}