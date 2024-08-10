package com.emojinious.emojinious_backend.controller;

import com.emojinious.emojinious_backend.dto.GameSettingsDto;
import com.emojinious.emojinious_backend.dto.GameStateDto;
import com.emojinious.emojinious_backend.service.GameSessionService;
import com.emojinious.emojinious_backend.service.GameService;
import com.emojinious.emojinious_backend.util.JwtUtil;
import com.emojinious.emojinious_backend.util.MessageUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class GameSessionController {
    private final GameSessionService gameSessionService;
    private final GameService gameService;
    private final JwtUtil jwtUtil;
    private final MessageUtil messageUtil;

    @PutMapping("/{sessionId}/settings")
    public ResponseEntity<?> updateGameSettings(
            @PathVariable String sessionId,
            @RequestBody GameSettingsDto settings,
            @RequestHeader("Authorization") String token) {

        Claims claims = jwtUtil.validateToken(token.replace("Bearer ", ""));
        String playerId = claims.getSubject();
        boolean isHost = claims.get("isHost", Boolean.class);
        String tokenSessionId = claims.get("sessionId", String.class);

        if (!isHost || !sessionId.equals(tokenSessionId)) {
            return ResponseEntity.status(403).body("Not authorized to change settings");
        }

        try {
            gameSessionService.updateGameSettings(sessionId, playerId, settings);
            GameStateDto updatedGameState = gameService.getGameState(sessionId);
            messageUtil.broadcastGameState(sessionId, updatedGameState);
            return ResponseEntity.ok().body("Settings updated successfully");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred while updating settings");
        }
    }
}