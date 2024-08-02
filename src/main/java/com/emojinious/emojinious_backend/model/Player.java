package com.emojinious.emojinious_backend.model;

import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Player implements Serializable {
    private String id;
    private String nickname;
    private int characterId;
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