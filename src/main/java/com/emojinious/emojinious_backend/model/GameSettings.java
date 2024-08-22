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
        this.promptTimeLimit = 40;
        this.guessTimeLimit = 30;
        this.difficulty = "NORMAL";
        this.turns = 1;
        this.theme = "무작위";
    }
}