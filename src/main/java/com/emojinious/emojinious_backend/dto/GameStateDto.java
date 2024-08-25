package com.emojinious.emojinious_backend.dto;

import com.emojinious.emojinious_backend.constant.GameState;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class GameStateDto {
    private String sessionId;
    private List<PlayerDto> players;
    private GameSettingsDto settings;
    private GameState state;
    private int currentTurn;
    private int currentPhase;
    private Map<String, String> currentPrompts;
    private Map<String, String> currentGuesses;
    private long remainingTime;


}
