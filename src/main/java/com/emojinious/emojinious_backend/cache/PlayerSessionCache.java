package com.emojinious.emojinious_backend.cache;

import lombok.Data;
import org.springframework.data.redis.core.RedisHash;

@Data
@RedisHash("player_session")
public class PlayerSessionCache {
    private String id;
    private String nickname;
    private int characterId;
    private boolean isHost;
    private String sessionId;
    private String token;
}