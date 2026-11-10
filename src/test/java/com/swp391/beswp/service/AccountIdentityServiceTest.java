package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountIdentityServiceTest {
    UserRepository users = mock(UserRepository.class);
    AuditLogService audit = mock(AuditLogService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    AccountIdentityService service = new AccountIdentityService(users, audit, events);
    @Test void resolvingSourceDoesNotMarkEmailVerifiedAndRevokesPreviousSessions() {
        User user = new User(); user.setId(1); user.setAuthProvider("UNKNOWN");
        Role role = new Role(); role.setRoleName("Member"); user.setRole(role);
        when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user));
        var result = service.resolve(1, "GOOGLE", "Đã đối chiếu hồ sơ");
        assertEquals("GOOGLE", result.authProvider()); assertFalse(result.emailVerified());
        assertEquals(1, user.getSessionVersion()); verify(events).publishEvent(new AccountSessionsInvalidated(1));
    }
    @Test void resolvedSourceCannotBeSilentlyOverwritten() {
        User user = new User(); when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.resolve(1, "GOOGLE", "review")).getStatusCode().value());
        verifyNoInteractions(events);
    }
}
