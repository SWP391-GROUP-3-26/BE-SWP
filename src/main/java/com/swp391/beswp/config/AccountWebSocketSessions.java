package com.swp391.beswp.config;

import com.swp391.beswp.service.AccountSessionsInvalidated;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Close already connected chat clients after a password/session change commits. */
@Component
public class AccountWebSocketSessions {
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> users = new ConcurrentHashMap<>();
    public void opened(WebSocketSession session) { sessions.put(session.getId(), session); }
    public void closed(String id) { sessions.remove(id); users.remove(id); }
    public void authenticated(String sessionId, Integer userId) { users.put(sessionId, userId); }
    @TransactionalEventListener
    public void invalidate(AccountSessionsInvalidated event) {
        users.forEach((id, userId) -> {
            if (userId.equals(event.userId())) {
                WebSocketSession session = sessions.get(id);
                if (session != null) {
                    try { session.close(CloseStatus.POLICY_VIOLATION); }
                    catch (IOException ignored) { /* A disconnected transport no longer delivers messages. */ }
                }
                closed(id);
            }
        });
    }
}
