package com.swp391.beswp.config;

import com.swp391.beswp.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AccountWebSocketSessions accountSessions;

    @Override
    public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new org.springframework.web.socket.handler.WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session) throws Exception {
                accountSessions.opened(session);
                super.afterConnectionEstablished(session);
            }
            @Override public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session,
                    org.springframework.web.socket.CloseStatus status) throws Exception {
                accountSessions.closed(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                } else if (StompCommand.SEND.equals(accessor.getCommand())
                        || StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeDestination(accessor);
                }
                return message;
            }
        });
    }

    private void authorizeDestination(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("WebSocket authentication is required");
        }
        Object token = accessor.getSessionAttributes().get("accessToken");
        if (!(token instanceof String)) throw new MessageDeliveryException("WebSocket session has expired");
        try { jwtService.validateToken((String) token); }
        catch (RuntimeException ex) { throw new MessageDeliveryException("WebSocket session has expired"); }

        String destination = accessor.getDestination();
        if (StompCommand.SEND.equals(accessor.getCommand()) && !"/app/chat.send".equals(destination)) {
            throw new MessageDeliveryException("WebSocket send destination is not allowed");
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                && !"/user/queue/messages".equals(destination)) {
            throw new MessageDeliveryException("WebSocket subscription destination is not allowed");
        }
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("Missing WebSocket Authorization header");
        }

        try {
            Map<String, Object> claims = jwtService.validateToken(
                    authorization.substring(BEARER_PREFIX.length()));
            String principal = String.valueOf(claims.get("sub"));
            String role = String.valueOf(claims.get("role"));
            if (!"Member".equalsIgnoreCase(role) && !"Coach".equalsIgnoreCase(role)) {
                throw new MessageDeliveryException("Chat is only available to Member and Coach");
            }
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            ));
            accessor.getSessionAttributes().put("accessToken", authorization.substring(BEARER_PREFIX.length()));
            accountSessions.authenticated(accessor.getSessionId(), Integer.valueOf(principal));
        } catch (MessageDeliveryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MessageDeliveryException("Invalid WebSocket token");
        }
    }
}
