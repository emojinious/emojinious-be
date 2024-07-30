package com.emojinious.emojinious_backend.controller;

import com.emojinious.emojinious_backend.dto.GameSettingsDto;
import com.emojinious.emojinious_backend.service.GameSessionService;
import io.jsonwebtoken.Claims;
import com.emojinious.emojinious_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class GameSessionController {
    private final GameSessionService gameSessionService;
    private final JwtUtil jwtUtil;

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
            return ResponseEntity.ok().body("Settings updated successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}