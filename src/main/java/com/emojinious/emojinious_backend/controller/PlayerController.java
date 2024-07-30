package com.emojinious.emojinious_backend.controller;

import com.emojinious.emojinious_backend.cache.PlayerSessionCache;
import com.emojinious.emojinious_backend.dto.PlayerCreateRequest;
import com.emojinious.emojinious_backend.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {
    private final PlayerService playerService;

    @PostMapping("/host")
    public ResponseEntity<?> createHostPlayer(@Valid @RequestBody PlayerCreateRequest request) {
        PlayerSessionCache player = playerService.createHostPlayer(request.getNickname(), request.getCharacterId());
        String inviteLink = playerService.generateInviteLink(player.getSessionId());

        Map<String, Object> response = new HashMap<>();
        response.put("player", player);
        response.put("inviteLink", inviteLink);
        response.put("token", player.getToken());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/guest/{sessionId}")
    public ResponseEntity<?> createGuestPlayer(@PathVariable String sessionId,
                                               @Valid @RequestBody PlayerCreateRequest request) {
        PlayerSessionCache player = playerService.createGuestPlayer(request.getNickname(), request.getCharacterId(), sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("player", player);
        response.put("token", player.getToken());

        return ResponseEntity.ok(response);
    }

}