package com.emojinious.emojinious_backend.cache;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisHash;

@Data
@RedisHash("player_session")
@NoArgsConstructor
@AllArgsConstructor
public class Player implements Serializable {
    private String id;
    private String nickname;
    private int characterId;
    @JsonProperty("isHost")
    private boolean isHost;
    private int score;
    private String token;
    private String sessionId;


    public Player(String id, String nickname, int characterId, boolean isHost) {
        this.id = id;
        this.nickname = nickname;
        this.characterId = characterId;
        this.isHost = isHost;
        this.score = 0;
    }
}