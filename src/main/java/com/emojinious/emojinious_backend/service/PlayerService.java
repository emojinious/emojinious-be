package com.emojinious.emojinious_backend.service;

import com.emojinious.emojinious_backend.cache.PlayerSessionCache;
import com.emojinious.emojinious_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;

    public PlayerSessionCache createHostPlayer(String nickname, int characterId) {
        PlayerSessionCache player = new PlayerSessionCache();
        player.setId(UUID.randomUUID().toString());
        player.setNickname(nickname);
        player.setCharacterId(characterId);
        player.setHost(true);
        player.setSessionId(UUID.randomUUID().toString());

        savePlayerToCache(player);
        player.setToken(jwtUtil.generateToken(player.getId(), player.getSessionId(), true));
        return player;
    }

    public PlayerSessionCache createGuestPlayer(String nickname, int characterId, String sessionId) {
        PlayerSessionCache player = new PlayerSessionCache();
        player.setId(UUID.randomUUID().toString());
        player.setNickname(nickname);
        player.setCharacterId(characterId);
        player.setHost(false);
        player.setSessionId(sessionId);

        savePlayerToCache(player);
        player.setToken(jwtUtil.generateToken(player.getId(), player.getSessionId(), false));
        return player;
    }

    private void savePlayerToCache(PlayerSessionCache player) {
        redisTemplate.opsForValue().set("player:" + player.getId(), player);
        // 세션에 플레이어 추가
        redisTemplate.opsForSet().add("session:" + player.getSessionId(), player.getId());
    }

    public String generateInviteLink(String sessionId) {
        return "test.com/invite/" + sessionId;
    }
}
