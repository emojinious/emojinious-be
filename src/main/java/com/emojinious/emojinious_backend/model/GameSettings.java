package com.emojinious.emojinious_backend.model;

import java.io.Serializable;
import lombok.Data;

@Data
public class GameSettings implements Serializable {
    private int promptTimeLimit;
    private int guessTimeLimit;
    private String difficulty;
    private int turns;
    private String theme;


    public GameSettings() {
        // Default settings
        this.promptTimeLimit = 20;
        this.guessTimeLimit = 15;
        this.difficulty = "NORMAL";
        this.turns = 3;
        this.theme = "RANDOM";
    }
}