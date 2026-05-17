package com.chatroom.config;

import com.chatroom.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * Replaces this in server.js:
 *
 *   const io = new Server(server, { cors: { origin: "*" }, maxHttpBufferSize: 15MB })
 *   io.use(async (socket, next) => {
 *     const token = socket.handshake.auth?.token ...
 *     const payload = verifyToken(token)
 *     socket.user = safeUser(user)
 *     next()
 *   })
 *
 * Frontend uses SockJS + STOMP instead of socket.io-client.
 * All socket.on/emit patterns become @MessageMapping methods.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // "/topic/..." — server broadcasts to all subscribers (like io.to(room).emit)
        config.enableSimpleBroker("/topic");
        // "/app/..." — client sends to server (like socket.emit)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
            .addEndpoint("/ws")           // ws://host/ws  (replaces Socket.io endpoint)
            .setAllowedOriginPatterns("*")
            .withSockJS();                // SockJS fallback (like Socket.io transport fallback)
    }

    /**
     * WebSocket authentication interceptor
     * Equivalent to: io.use(async (socket, next) => { verifyToken(...); next() })
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                    MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Client sends token in STOMP headers at connect time
                    List<String> tokenList = accessor.getNativeHeader("token");
                    String token = (tokenList != null && !tokenList.isEmpty()) ? tokenList.get(0) : null;

                    if (token != null) {
                        String userId = jwtUtil.extractUserId(token);
                        if (userId != null) {
                            accessor.setUser(new UsernamePasswordAuthenticationToken(
                                userId, null, Collections.emptyList()
                            ));
                        }
                    }
                }
                return message;
            }
        });
    }
}
