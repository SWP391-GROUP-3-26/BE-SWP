package com.swp391.beswp.service;

import com.swp391.beswp.dto.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthEmailVerificationTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder passwords;
    @Mock JwtService jwt;
    @Mock AuditLogService audit;
    @Mock EmailOtpService otp;
    AuthService auth;
    User user;
    LoginRequest login;

    @BeforeEach void setup() {
        auth = new AuthService(users, roles, passwords, jwt, audit, otp);
        user = new User(); user.setId(1); user.setUsername("member"); user.setEmail("member@example.com");
        user.setPassword("hashed"); user.setStatus("Active");
        Role role = new Role(); role.setRoleName("Member"); user.setRole(role);
        login = new LoginRequest(); login.setIdentifier("member"); login.setPassword("Password1!");
    }
    void credentials() {
        when(users.findByUsernameIgnoreCase("member")).thenReturn(Optional.of(user));
        when(passwords.matches("Password1!", "hashed")).thenReturn(true);
    }
    @Test void unverifiedMemberGetsOnlyRestrictedChallengeNotAnAccessToken() {
        credentials();
        when(otp.loginChallenge(user)).thenReturn(new EmailOtpDtos.Challenge(user.getEmail(), "proof", Instant.now(), 300, 0));
        var result = auth.login(login);
        assertTrue(result.getData().isEmailVerificationRequired());
        assertNull(result.getData().getAccessToken()); assertEquals("proof", result.getData().getEmailVerification().verificationToken());
        verifyNoInteractions(jwt);
    }
    @Test void verifiedMemberAndUnverifiedStaffCanLoginNormally() {
        credentials(); user.setEmailVerified(true); when(jwt.generateToken(user)).thenReturn("jwt");
        assertEquals("jwt", auth.login(login).getData().getAccessToken());
        user.setEmailVerified(false); user.getRole().setRoleName("Admin");
        assertEquals("jwt", auth.login(login).getData().getAccessToken()); verifyNoInteractions(otp);
    }
    @Test void incorrectPasswordCannotObtainVerificationGrant() {
        when(users.findByUsernameIgnoreCase("member")).thenReturn(Optional.of(user));
        assertThrows(ResponseStatusException.class, () -> auth.login(login)); verifyNoInteractions(otp, jwt);
    }
    @Test void googleAccountCannotUseLocalPasswordFlow() {
        credentials(); user.setAuthProvider("GOOGLE");
        assertThrows(ResponseStatusException.class, () -> auth.login(login)); verifyNoInteractions(otp, jwt);
    }
    @Test void googleLoginProvesEmailOwnershipWithoutConvertingExistingLocalProvider() {
        when(users.findIdByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user.getId()));
        when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user));
        auth.loginWithGoogle(user.getEmail(), "Member", null);
        assertTrue(user.isEmailVerified()); assertEquals("LOCAL", user.getAuthProvider()); verifyNoInteractions(otp);
    }
}
