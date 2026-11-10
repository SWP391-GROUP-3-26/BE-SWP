package com.swp391.beswp.service;

import com.swp391.beswp.dto.EmailOtpDtos.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class EmailOtpServiceTest {
    @Mock EmailOtpChallengeRepository challenges;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock OtpMailService mail;
    @Mock AuditLogService audit;
    @Mock ApplicationEventPublisher events;
    @Mock Clock clock;
    EmailOtpService service;
    EmailOtpChallenge row;
    User user;
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    Instant now = Instant.parse("2026-11-10T00:00:00Z");

    @BeforeEach void setup() {
        service = new EmailOtpService(challenges, users, roles, encoder, mail, audit, events, clock);
        lenient().when(clock.instant()).thenAnswer(i -> now);
        row = new EmailOtpChallenge(); row.setEmail("member@example.com");
        lenient().when(challenges.lockByEmail(row.getEmail())).thenReturn(Optional.of(row));
        user = new User(); user.setId(7); user.setEmail(row.getEmail()); user.setUsername("member");
        user.setFullName("Member"); user.setPhone("0901234567"); user.setStatus("Active");
        user.setPassword(encoder.encode("Password1!"));
        Role role = new Role(); role.setRoleName("Member"); user.setRole(role);
    }
    void existingUser() {
        when(users.findIdByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user.getId()));
        lenient().when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
    }
    String sentOtp() {
        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(row.getEmail()), anyString(), otp.capture());
        return otp.getValue();
    }
    VerifyRequest verifyRequest(Challenge challenge, String otp) {
        return new VerifyRequest(challenge.email(), challenge.verificationToken(), otp);
    }
    void status(int code, Runnable action) {
        assertEquals(code, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }
    @Test void registrationStoresOnlyHashesAndCreatesNoAccountUntilVerification() {
        Challenge challenge = service.startRegistration(user);
        String otp = sentOtp();
        assertTrue(otp.matches("[0-9]{6}")); assertNotEquals(otp, row.getOtpHash());
        assertTrue(encoder.matches(otp, row.getOtpHash())); assertEquals(user.getPassword(), row.getPendingPasswordHash());
        assertNotEquals(challenge.verificationToken(), row.getContextHash());
        verify(users, never()).saveAndFlush(any());
        when(roles.findByRoleNameIgnoreCase("Member")).thenReturn(Optional.of(user.getRole()));
        when(users.saveAndFlush(any())).thenAnswer(i -> { User saved = i.getArgument(0); saved.setId(8); return saved; });
        var result = service.verifyRegistration(verifyRequest(challenge, otp));
        assertEquals(8, result.getData().getUserId());
        verify(users).saveAndFlush(argThat(u -> u.isEmailVerified() && "LOCAL".equals(u.getAuthProvider())));
        assertNull(row.getPendingPasswordHash()); assertNull(row.getContextHash());
        status(400, () -> service.verifyRegistration(verifyRequest(challenge, otp)));
    }
    @Test void duplicateAppearingWhileWaitingDoesNotCreateAccount() {
        Challenge challenge = service.startRegistration(user);
        when(users.existsByEmailIgnoreCase(user.getEmail())).thenReturn(true);
        String otp = sentOtp();
        status(409, () -> service.verifyRegistration(verifyRequest(challenge, otp)));
        verify(users, never()).saveAndFlush(any());
    }
    @Test void fiveWrongAttemptsBlockEvenCorrectOtp() {
        Challenge challenge = service.startRegistration(user);
        String otp = sentOtp(); String wrong = "000000".equals(otp) ? "000001" : "000000";
        for (int i = 0; i < 5; i++) status(400, () -> service.verifyRegistration(verifyRequest(challenge, wrong)));
        assertEquals(5, row.getFailedAttempts());
        status(400, () -> service.verifyRegistration(verifyRequest(challenge, otp)));
        verify(users, never()).saveAndFlush(any());
    }
    @Test void springTransactionCommitsWrongAttemptCounterDespiteReturningAnOtpError() {
        Challenge challenge = service.startRegistration(user); String otp = sentOtp();
        String wrong = "000000".equals(otp) ? "000001" : "000000";
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactions,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        EmailOtpService proxy = (EmailOtpService) factory.getProxy();
        status(400, () -> proxy.verifyRegistration(verifyRequest(challenge, wrong)));
        assertEquals(1, row.getFailedAttempts());
        verify(transactions).commit(any()); verify(transactions, never()).rollback(any());
    }
    @Test void springTransactionRollsBackOtpChangesWhenSmtpFails() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "SMTP unavailable"))
                .when(mail).send(anyString(), anyString(), anyString());
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactions,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        EmailOtpService proxy = (EmailOtpService) factory.getProxy();
        status(503, () -> proxy.startRegistration(user));
        verify(transactions).rollback(any()); verify(transactions, never()).commit(any());
    }
    @Test void otpExpiresExactlyAtFiveMinutes() {
        Challenge challenge = service.startRegistration(user); String otp = sentOtp();
        now = now.plusSeconds(300);
        status(400, () -> service.verifyRegistration(verifyRequest(challenge, otp)));
    }
    @Test void contextCannotBeUsedForAnotherEmailOrPurpose() {
        Challenge challenge = service.startRegistration(user);
        status(400, () -> service.verifyReset(verifyRequest(challenge, sentOtp())));
        status(400, () -> service.resend(new ContextRequest("other@example.com", challenge.verificationToken()), "REGISTER"));
    }
    @Test void resendCooldownInvalidatesOldHashAndPreservesHourlyBudget() {
        Challenge challenge = service.startRegistration(user); String oldHash = row.getOtpHash();
        ContextRequest request = new ContextRequest(challenge.email(), challenge.verificationToken());
        status(429, () -> service.resend(request, "REGISTER"));
        now = now.plusSeconds(60); service.resend(request, "REGISTER");
        assertNotEquals(oldHash, row.getOtpHash()); assertEquals(2, row.getSendCount());
    }
    @Test void changingPurposeOrCreatingNewRequestsCannotBypassEmailSendBudget() {
        for (int i = 0; i < 5; i++) { service.startRegistration(user); now = now.plusSeconds(60); }
        status(429, () -> service.startRegistration(user));
        now = now.plusSeconds(3600); service.startRegistration(user); assertEquals(1, row.getSendCount());
    }
    @Test void hourlyLimitUsesRollingWindowAcrossTheFirstHoursBoundary() {
        Instant start = now;
        for (long seconds : new long[]{0, 3500, 3560, 3620, 3680, 3740}) {
            now = start.plusSeconds(seconds); service.startRegistration(user);
        }
        now = start.plusSeconds(3800);
        status(429, () -> service.startRegistration(user));
        assertEquals(5, row.getSendCount());
    }
    @Test void memberLoginGrantDoesNotSendUntilRequestedAndCannotAccessOtherPurposes() {
        Challenge challenge = service.loginChallenge(user); verifyNoInteractions(mail);
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        ContextRequest context = new ContextRequest(challenge.email(), challenge.verificationToken());
        status(400, () -> service.resend(context, "RESET"));
        service.resend(context, "VERIFY_EMAIL");
        service.verifyMember(verifyRequest(challenge, sentOtp()));
        assertTrue(user.isEmailVerified()); assertNull(row.getOtpHash());
    }
    @Test void blockedOrChangedAccountCannotFinishVerification() {
        Challenge challenge = service.loginChallenge(user);
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        user.setSessionVersion(1);
        status(400, () -> service.resend(new ContextRequest(challenge.email(), challenge.verificationToken()), "VERIFY_EMAIL"));
        verifyNoInteractions(mail);
    }
    @Test void resetRequiresProofAndInvalidatesAllOldSessionsOnlyAfterPasswordSaved() {
        existingUser(); Challenge challenge = service.requestPasswordReset(user.getEmail());
        String otp = sentOtp();
        status(400, () -> service.resetPassword(new ResetRequest(user.getEmail(), "forged", "NewPassword1!", "NewPassword1!")));
        ResetGrant grant = service.verifyReset(verifyRequest(challenge, otp));
        assertEquals(0, user.getSessionVersion()); assertNull(row.getOtpHash());
        status(400, () -> service.verifyReset(verifyRequest(challenge, otp)));
        ResetRequest request = new ResetRequest(user.getEmail(), grant.resetToken(), "NewPassword1!", "NewPassword1!");
        service.resetPassword(request);
        assertTrue(encoder.matches("NewPassword1!", user.getPassword())); assertEquals(1, user.getSessionVersion());
        assertFalse(user.isEmailVerified());
        verify(events).publishEvent(new AccountSessionsInvalidated(7));
        status(400, () -> service.resetPassword(request));
    }
    @Test void resetGrantExpiresAfterFiveMinutes() {
        existingUser(); Challenge challenge = service.requestPasswordReset(user.getEmail());
        ResetGrant grant = service.verifyReset(verifyRequest(challenge, sentOtp())); now = now.plusSeconds(300);
        status(400, () -> service.resetPassword(new ResetRequest(user.getEmail(), grant.resetToken(), "NewPassword1!", "NewPassword1!")));
    }
    @Test void googleUnknownInactiveAndMissingEmailsCannotReset() {
        existingUser(); user.setAuthProvider("GOOGLE"); status(403, () -> service.requestPasswordReset(user.getEmail()));
        user.setAuthProvider("UNKNOWN"); status(409, () -> service.requestPasswordReset(user.getEmail()));
        user.setAuthProvider("LOCAL"); user.setStatus("Locked"); status(403, () -> service.requestPasswordReset(user.getEmail()));
        when(users.findIdByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.empty());
        status(404, () -> service.requestPasswordReset(user.getEmail())); verifyNoInteractions(mail);
    }
}
