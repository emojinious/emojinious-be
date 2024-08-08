package com.emojinious.emojinious_backend.dto;

import lombok.Data;

@Data
public class GameSettingsDto {
    private Integer promptTimeLimit;
    private Integer guessTimeLimit;
    private String difficulty;
    private Integer turns;
    private String theme;
}