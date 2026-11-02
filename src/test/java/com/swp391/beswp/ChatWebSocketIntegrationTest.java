package com.swp391.beswp;

import com.swp391.beswp.dto.ChatDtos;
import com.swp391.beswp.entity.Role;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.service.ChatService;
import com.swp391.beswp.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTest {
    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper mapper;
    @Autowired SimpUserRegistry users;
    @MockitoBean ChatService chatService;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void memberAndCoachCanSubscribeAndExchangeMessagesFromPreviewOrigins() throws Exception {
        var response = new ChatDtos.MessageResponse(1L, 1, 900001, "Member", null,
                900002, "Coach", null, "Xin chào", "TEXT", null, false, LocalDateTime.now());
        when(chatService.send(eq("900001"), any())).thenReturn(response);

        try (var member = connect("http://127.0.0.1:5174");
             var coach = connect("http://localhost:5173")) {
            member.login(token(900001, "Member"));
            coach.login(token(900002, "Coach"));
            member.send("SUBSCRIBE\nid:messages\ndestination:/user/queue/messages\nack:auto\n\n\0");
            coach.send("SUBSCRIBE\nid:messages\ndestination:/user/queue/messages\nack:auto\n\n\0");
            await().atMost(Duration.ofSeconds(5)).until(() -> subscribed("900001") && subscribed("900002"));

            member.send("SEND\ndestination:/app/chat.send\ncontent-type:application/json\n\n"
                    + "{\"receiverId\":900002,\"content\":\"Xin chào\",\"messageType\":\"TEXT\"}\0");
            assertTrue(member.nextFrame().startsWith("MESSAGE\n"));
            String received = coach.nextFrame();
            assertTrue(received.startsWith("MESSAGE\n"), received);
            var payload = mapper.readTree(received.substring(received.indexOf("\n\n") + 2).replace("\0", ""));
            assertEquals("Xin chào", payload.path("content").asText());
            assertEquals(900001, payload.path("senderId").asInt());
            verify(chatService).send("900001", new ChatDtos.SendRequest(900002, "Xin chào", "TEXT", null));
        }
    }

    @Test
    void invalidTokensAndNonChatRolesCannotConnect() throws Exception {
        for (String token : List.of("invalid-token", token(900003, "Receptionist"))) {
            try (var socket = connect("http://localhost:5174")) {
                socket.sendConnect(token);
                assertTrue(socket.nextFrame().startsWith("ERROR\n"));
            }
        }
        verifyNoInteractions(chatService);
    }

    @Test
    void authenticatedUserCannotSubscribeToAnotherDestination() throws Exception {
        try (var socket = connect("http://localhost:5173")) {
            socket.login(token(900004, "Coach"));
            socket.send("SUBSCRIBE\nid:forbidden\ndestination:/topic/messages\n\n\0");
            assertTrue(socket.nextFrame().startsWith("ERROR\n"));
        }
    }

    @Test
    void httpAndSockJsAcceptOnlyConfiguredOrigins() throws Exception {
        for (String path : List.of("/ws/info", "/api/chat/contacts")) {
            for (String origin : List.of("http://localhost:5173", "http://127.0.0.1:5173",
                    "http://localhost:5174", "http://127.0.0.1:5174")) {
                var result = preflight(path, origin);
                assertEquals(200, result.statusCode(), path + " " + origin);
                assertEquals(origin, result.headers().firstValue("access-control-allow-origin").orElseThrow());
            }
            assertEquals(403, preflight(path, "https://untrusted.example").statusCode());
        }
    }

    private HttpResponse<String> preflight(String path, String origin) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Origin", origin).header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private boolean subscribed(String userId) {
        var user = users.getUser(userId);
        return user != null && user.getSessions().stream().anyMatch(session -> !session.getSubscriptions().isEmpty());
    }

    private String token(int id, String role) {
        var chatRole = new Role();
        chatRole.setRoleName(role);
        var user = new User();
        user.setId(id);
        user.setUsername("chat-test");
        user.setRole(chatRole);
        return jwtService.generateToken(user);
    }

    private Socket connect(String origin) throws Exception {
        var socket = new Socket();
        socket.connection = client.newWebSocketBuilder().header("Origin", origin)
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/000/"
                        + java.util.UUID.randomUUID() + "/websocket"), socket).get(5, TimeUnit.SECONDS);
        assertEquals("o", socket.frames.poll(5, TimeUnit.SECONDS));
        return socket;
    }

    private class Socket implements WebSocket.Listener, AutoCloseable {
        private WebSocket connection;
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);
                if (payload.startsWith("a")) {
                    for (String frame : mapper.readValue(payload.substring(1), String[].class)) frames.add(frame);
                } else if (!payload.equals("h")) {
                    frames.add(payload);
                }
            }
            webSocket.request(1);
            return null;
        }

        void send(String frame) {
            connection.sendText(mapper.writeValueAsString(List.of(frame)), true).join();
        }

        void sendConnect(String token) {
            send("CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer " + token + "\n\n\0");
        }

        void login(String token) throws Exception {
            sendConnect(token);
            String frame = nextFrame();
            assertTrue(frame.startsWith("CONNECTED\n"), frame);
        }

        String nextFrame() throws Exception {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertNotNull(frame, "Timed out waiting for STOMP response");
            return frame;
        }

        @Override
        public void close() {
            connection.abort();
        }
    }
}
