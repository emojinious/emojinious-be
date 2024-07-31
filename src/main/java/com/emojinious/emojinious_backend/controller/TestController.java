package com.emojinious.emojinious_backend.controller;

import com.emojinious.emojinious_backend.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class TestController {

    @Autowired
    private GameWebSocketHandler webSocketHandler;

    @PostMapping("/api/test/websocket")
    public String testWebSocket(@RequestBody String message) throws IOException {
        webSocketHandler.sendMessageToAll(message);
        return "Message sent to all WebSocket clients";
    }
}