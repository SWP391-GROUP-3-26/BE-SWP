package com.swp391.beswp.config;

import com.swp391.beswp.service.AccountSessionsInvalidated;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import static org.mockito.Mockito.*;

class AccountWebSocketSessionsTest {
    @Test void passwordResetClosesAllSocketsForThatAccountOnly() throws Exception {
        AccountWebSocketSessions sessions = new AccountWebSocketSessions();
        WebSocketSession first = mock(WebSocketSession.class), second = mock(WebSocketSession.class), other = mock(WebSocketSession.class);
        when(first.getId()).thenReturn("a"); when(second.getId()).thenReturn("b"); when(other.getId()).thenReturn("c");
        sessions.opened(first); sessions.opened(second); sessions.opened(other);
        sessions.authenticated("a", 7); sessions.authenticated("b", 7); sessions.authenticated("c", 8);
        sessions.invalidate(new AccountSessionsInvalidated(7));
        verify(first).close(CloseStatus.POLICY_VIOLATION); verify(second).close(CloseStatus.POLICY_VIOLATION);
        verify(other, never()).close(any());
    }
}
