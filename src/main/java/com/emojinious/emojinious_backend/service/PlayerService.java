package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.Player;
import com.emojinious.emojinious_backend.util.JwtUtil;
import com.emojinious.emojinious_backend.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {
    private final RedisUtil redisUtil;
    private final JwtUtil jwtUtil;

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

        redisUtil.setWithExpiration(playerKey, player, PLAYER_EXPIRATION, TimeUnit.SECONDS);
        redisUtil.setWithExpiration(sessionPlayerKey, player.getId(), PLAYER_EXPIRATION, TimeUnit.SECONDS);
    }

    public Player getPlayerById(String playerId) {
        return redisUtil.get(PLAYER_KEY + playerId, Player.class);
    }

    public Player getPlayerBySessionId(String sessionId) {
        String playerId = redisUtil.get(SESSION_PLAYER_KEY + sessionId, String.class);
        return playerId != null ? getPlayerById(playerId) : null;
    }

    public void removePlayer(String playerId) {
        Player player = getPlayerById(playerId);
        if (player != null) {
            redisUtil.delete(PLAYER_KEY + playerId);
            redisUtil.delete(SESSION_PLAYER_KEY + player.getSessionId());
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
        return redisUtil.hasKey(SESSION_PLAYER_KEY + sessionId);
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
        return "http://localhost:3000/join?sessionId=" + sessionId;
    }
}