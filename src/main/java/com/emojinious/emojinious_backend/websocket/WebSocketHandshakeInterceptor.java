    package com.emojinious.emojinious_backend.websocket;

    import com.emojinious.emojinious_backend.service.PlayerService;
    import com.emojinious.emojinious_backend.util.JwtUtil;
    import org.springframework.http.server.ServerHttpRequest;
    import org.springframework.http.server.ServerHttpResponse;
    import org.springframework.web.socket.WebSocketHandler;
    import org.springframework.web.socket.server.HandshakeInterceptor;

    import java.util.Map;

    // 일단 사용안함
    public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

        private final JwtUtil jwtUtil;
        private final PlayerService playerService;

        public WebSocketHandshakeInterceptor(JwtUtil jwtUtil, PlayerService playerService) {
            this.jwtUtil = jwtUtil;
            this.playerService = playerService;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            String token = request.getHeaders().getFirst("X-Authorization");
            System.out.println("token = " + token);
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                try {
                    String playerId = jwtUtil.validateToken(token).getSubject();
                    var player = playerService.getPlayerById(playerId);
                    if (player != null) {
                        attributes.put("playerId", player.getId());
                        attributes.put("sessionId", player.getSessionId());
                        attributes.put("nickname", player.getNickname());
                        return true;
                    }
                } catch (Exception e) {
                    System.out.println("Invalid token: " + e.getMessage());
                }
            }
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // x
        }
    }