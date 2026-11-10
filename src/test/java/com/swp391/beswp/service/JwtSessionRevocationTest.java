package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtSessionRevocationTest {
    @Mock UserRepository users;
    JwtService jwt;
    User user;
    @BeforeEach void setup() {
        jwt = new JwtService("test-secret-not-used-in-production", 3600, users);
        user = new User(); user.setId(1); user.setStatus("Active"); user.setEmailVerified(true);
        Role role = new Role(); role.setRoleName("Member"); user.setRole(role);
        when(users.findById(1)).thenReturn(Optional.of(user));
    }
    @Test void passwordResetInvalidatesEveryPreviouslyIssuedToken() {
        String first = jwt.generateToken(user); String second = jwt.generateToken(user);
        assertEquals(0L, jwt.validateToken(first).get("sv"));
        user.setSessionVersion(1);
        assertThrows(IllegalArgumentException.class, () -> jwt.validateToken(first));
        assertThrows(IllegalArgumentException.class, () -> jwt.validateToken(second));
        assertEquals(1L, jwt.validateToken(jwt.generateToken(user)).get("sv"));
    }
    @Test void unverifiedMemberCannotUseAnySignedAccessToken() {
        String token = jwt.generateToken(user); user.setEmailVerified(false);
        assertThrows(IllegalArgumentException.class, () -> jwt.validateToken(token));
    }
    @Test void staffDoesNotNeedEmailVerification() {
        user.getRole().setRoleName("Admin"); user.setEmailVerified(false);
        assertNotNull(jwt.validateToken(jwt.generateToken(user)));
    }
    @Test void lockedOrDeletedAccountCannotUseOldTokens() {
        String token = jwt.generateToken(user); user.setStatus("Locked");
        assertThrows(IllegalArgumentException.class, () -> jwt.validateToken(token));
        when(users.findById(1)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> jwt.validateToken(token));
    }
    @Test void roleChangeInvalidatesOldAuthorityClaims() {
        String token = jwt.generateToken(user); user.getRole().setRoleName("Coach");
        assertThrows(IllegalArgumentException.class, () -> jwt.validateToken(token));
    }
    @Test void legacySignedTokenWithoutSessionVersionRequiresLoginAgain() throws Exception {
        String[] parts = jwt.generateToken(user).split("\\.");
        var charset = java.nio.charset.StandardCharsets.UTF_8;
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), charset).replace("\"sv\":0,", "");
        parts[1] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(charset));
        String signingInput = parts[0] + "." + parts[1];
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-secret-not-used-in-production".getBytes(charset), "HmacSHA256"));
        String signature = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(charset)));
        assertThrows(IllegalArgumentException.class, () -> jwt.validateToken(signingInput + "." + signature));
        verify(users).findById(1);
    }
}
