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
class EmailChangeServiceTest {
    @Mock EmailOtpChallengeRepository challenges;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock OtpMailService mail;
    @Mock AuditLogService audit;
    @Mock ApplicationEventPublisher events;
    @Mock Clock clock;
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    EmailOtpService service;
    User user;
    Map<String, EmailOtpChallenge> rows;
    Set<String> unavailable;
    Instant now = Instant.parse("2026-11-10T00:00:00Z");
    static final String ORIGINAL = "fake@example.invalid", TARGET = "real@example.com";

    @BeforeEach void setup() {
        service = new EmailOtpService(challenges, users, roles, encoder, mail, audit, events, clock);
        lenient().when(clock.instant()).thenAnswer(i -> now);
        rows = new HashMap<>(); unavailable = new HashSet<>();
        lenient().when(challenges.lockByEmail(anyString())).thenAnswer(i -> Optional.ofNullable(rows.get(i.getArgument(0))));
        lenient().when(challenges.saveAndFlush(any())).thenAnswer(i -> {
            EmailOtpChallenge row = i.getArgument(0); rows.put(row.getEmail(), row); return row;
        });
        user = new User(); user.setId(7); user.setUsername("member"); user.setEmail(ORIGINAL); user.setStatus("Active");
        user.setPassword(encoder.encode("Password1!"));
        Role role = new Role(); role.setRoleName("Member"); user.setRole(role);
        lenient().when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        lenient().when(users.findEmailById(7)).thenAnswer(i -> Optional.of(user.getEmail()));
        lenient().when(users.existsByEmailIgnoreCase(anyString())).thenAnswer(i -> {
            String email = i.getArgument(0); return user.getEmail().equalsIgnoreCase(email) || unavailable.contains(email);
        });
        lenient().when(users.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }
    Challenge unverifiedChange() {
        Challenge login = service.loginChallenge(user);
        return service.requestVerificationEmailChange(new VerificationEmailChangeRequest(ORIGINAL, login.verificationToken(), TARGET));
    }
    Challenge profileChange() {
        user.setEmailVerified(true);
        return service.requestProfileEmailChange(7, new ProfileEmailChangeRequest(TARGET, "Password1!"));
    }
    String otp(String email) {
        ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), anyString(), codes.capture()); return codes.getValue();
    }
    VerifyRequest proof(Challenge challenge, String otp) { return new VerifyRequest(challenge.email(), challenge.verificationToken(), otp); }
    ContextRequest context(Challenge challenge) { return new ContextRequest(challenge.email(), challenge.verificationToken()); }
    void status(int code, Runnable action) {
        assertEquals(code, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }

    @Test void fakeEmailCanBeReplacedOnlyAfterOtpToNewEmailAndLoginGrantIsConsumed() {
        Challenge login = service.loginChallenge(user);
        Challenge change = service.requestVerificationEmailChange(new VerificationEmailChangeRequest(ORIGINAL, login.verificationToken(), " REAL@EXAMPLE.COM "));
        assertEquals(TARGET, change.email()); assertEquals(ORIGINAL, user.getEmail()); assertFalse(user.isEmailVerified());
        assertNull(rows.get(ORIGINAL).getContextHash());
        verify(mail, never()).send(eq(ORIGINAL), anyString(), anyString());
        status(400, () -> service.resend(new ContextRequest(ORIGINAL, login.verificationToken()), "VERIFY_EMAIL"));
        String code = otp(TARGET);
        service.verifyEmailChange(proof(change, code), "VERIFY_CHANGE_EMAIL", null);
        assertEquals(TARGET, user.getEmail()); assertTrue(user.isEmailVerified()); assertEquals(1, user.getSessionVersion());
        verify(events).publishEvent(new AccountSessionsInvalidated(7));
        status(400, () -> service.verifyEmailChange(proof(change, code), "VERIFY_CHANGE_EMAIL", null));
    }
    @Test void invalidLoginGrantCannotRequestEmailChange() {
        service.loginChallenge(user);
        status(400, () -> service.requestVerificationEmailChange(new VerificationEmailChangeRequest(ORIGINAL, "forged", TARGET)));
        assertEquals(ORIGINAL, user.getEmail()); verifyNoInteractions(mail);
    }
    @Test void invalidLegacyEmailSyntaxDoesNotPreventReplacingItUsingPasswordProof() {
        user.setEmail("invalid-legacy-email");
        Challenge login = service.loginChallenge(user);
        Challenge change = service.requestVerificationEmailChange(new VerificationEmailChangeRequest(user.getEmail(), login.verificationToken(), TARGET));
        verify(mail, never()).send(eq("invalid-legacy-email"), anyString(), anyString());
        service.verifyEmailChange(proof(change, otp(TARGET)), "VERIFY_CHANGE_EMAIL", null);
        assertEquals(TARGET, user.getEmail()); assertTrue(user.isEmailVerified());
    }
    @Test void smtpFailureDoesNotConsumeLoginGrantOrChangeUser() {
        Challenge login = service.loginChallenge(user);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "SMTP unavailable"))
                .when(mail).send(anyString(), anyString(), anyString());
        status(503, () -> service.requestVerificationEmailChange(new VerificationEmailChangeRequest(ORIGINAL, login.verificationToken(), TARGET)));
        assertNotNull(rows.get(ORIGINAL).getContextHash()); assertEquals(ORIGINAL, user.getEmail());
        assertFalse(user.isEmailVerified()); verifyNoInteractions(events);
    }
    @Test void unknownProviderCanVerifyNewEmailWithoutBeingReclassified() {
        user.setAuthProvider("UNKNOWN"); Challenge change = unverifiedChange();
        service.verifyEmailChange(proof(change, otp(TARGET)), "VERIFY_CHANGE_EMAIL", null);
        assertEquals("UNKNOWN", user.getAuthProvider()); assertTrue(user.isEmailVerified());
    }
    @Test void profileChangeRequiresCurrentPasswordAndLeavesOldEmailActiveWhileWaiting() {
        user.setEmailVerified(true);
        status(400, () -> service.requestProfileEmailChange(7, new ProfileEmailChangeRequest(TARGET, "WrongPassword1!")));
        verifyNoInteractions(mail);
        Challenge change = profileChange();
        assertEquals(ORIGINAL, user.getEmail()); assertTrue(user.isEmailVerified()); assertEquals(0, user.getSessionVersion());
        service.verifyEmailChange(proof(change, otp(TARGET)), "CHANGE_EMAIL", 7);
        assertEquals(TARGET, user.getEmail()); assertEquals(1, user.getSessionVersion());
    }
    @Test void profileTokenCannotBeUsedByAnotherUserOrByPublicVerificationEndpoint() {
        Challenge change = profileChange(); String code = otp(TARGET);
        status(400, () -> service.verifyEmailChange(proof(change, code), "CHANGE_EMAIL", 8));
        status(400, () -> service.verifyEmailChange(proof(change, code), "VERIFY_CHANGE_EMAIL", null));
        assertEquals(ORIGINAL, user.getEmail());
    }
    @Test void firstSuccessfulDestinationInvalidatesOtherPendingChanges() {
        Challenge first = profileChange(); String firstCode = otp(TARGET);
        Challenge second = service.requestProfileEmailChange(7, new ProfileEmailChangeRequest("second@example.com", "Password1!"));
        String secondCode = otp("second@example.com");
        service.verifyEmailChange(proof(first, firstCode), "CHANGE_EMAIL", 7);
        status(400, () -> service.verifyEmailChange(proof(second, secondCode), "CHANGE_EMAIL", 7));
        assertEquals(TARGET, user.getEmail());
    }
    @Test void changedEmailBetweenScalarLookupAndWriteLockRequiresRetry() {
        user.setEmailVerified(true);
        when(users.findEmailById(7)).thenReturn(Optional.of(ORIGINAL)); user.setEmail("changed@example.com");
        status(409, () -> service.requestProfileEmailChange(7, new ProfileEmailChangeRequest(TARGET, "Password1!")));
        verifyNoInteractions(mail);
    }
    @Test void emailChangeOtpCannotBeUsedForResetOrOrdinaryVerification() {
        Challenge change = unverifiedChange(); String code = otp(TARGET);
        status(400, () -> service.verifyReset(proof(change, code)));
        status(400, () -> service.verifyMember(proof(change, code)));
    }
    @Test void googleAccountsCannotChangeEmailInEitherFlow() {
        user.setAuthProvider("GOOGLE"); Challenge login = service.loginChallenge(user);
        status(403, () -> service.requestVerificationEmailChange(new VerificationEmailChangeRequest(ORIGINAL, login.verificationToken(), TARGET)));
        user.setEmailVerified(true);
        status(403, () -> service.requestProfileEmailChange(7, new ProfileEmailChangeRequest(TARGET, "Password1!")));
        verifyNoInteractions(mail);
    }
    @Test void sameOrAlreadyUsedDestinationIsRejectedBeforeSending() {
        user.setEmailVerified(true);
        status(400, () -> service.requestProfileEmailChange(7, new ProfileEmailChangeRequest(ORIGINAL.toUpperCase(), "Password1!")));
        unavailable.add(TARGET);
        status(409, this::profileChange); verifyNoInteractions(mail);
    }
    @Test void duplicateAppearingAfterOtpWasSentDoesNotChangeAccount() {
        Challenge change = profileChange(); String code = otp(TARGET); unavailable.add(TARGET);
        status(409, () -> service.verifyEmailChange(proof(change, code), "CHANGE_EMAIL", 7));
        assertEquals(ORIGINAL, user.getEmail()); assertEquals(0, user.getSessionVersion());
        verify(users, never()).saveAndFlush(any());
    }
    @Test void incorrectOtpLimitAndExpiryApplyToEmailChanges() {
        Challenge change = profileChange(); String code = otp(TARGET);
        String wrong = "000000".equals(code) ? "000001" : "000000";
        for (int i = 0; i < 5; i++) status(400, () -> service.verifyEmailChange(proof(change, wrong), "CHANGE_EMAIL", 7));
        status(400, () -> service.verifyEmailChange(proof(change, code), "CHANGE_EMAIL", 7));
        assertEquals(5, rows.get(TARGET).getFailedAttempts()); assertEquals(ORIGINAL, user.getEmail());
    }
    @Test void emailChangeOtpExpiresAtFiveMinutes() {
        Challenge change = profileChange(); String code = otp(TARGET); now = now.plusSeconds(300);
        status(400, () -> service.verifyEmailChange(proof(change, code), "CHANGE_EMAIL", 7));
    }
    @Test void resendUsesTheSameRecipientBudgetAcrossPurposes() {
        Challenge change = profileChange(); String old = otp(TARGET);
        status(429, () -> service.resendEmailChange(context(change), "CHANGE_EMAIL", 7));
        now = now.plusSeconds(60); service.resendEmailChange(context(change), "CHANGE_EMAIL", 7);
        status(400, () -> service.verifyEmailChange(proof(change, old), "CHANGE_EMAIL", 7));
        assertEquals(2, rows.get(TARGET).getSendCount());
    }
    @Test void passwordResetOrAdminEmailChangeInvalidatesPendingDestination() {
        Challenge change = profileChange(); String code = otp(TARGET); user.setSessionVersion(1);
        status(400, () -> service.verifyEmailChange(proof(change, code), "CHANGE_EMAIL", 7));
        user.setSessionVersion(0); user.setEmail("changed@example.com");
        status(400, () -> service.verifyEmailChange(proof(change, code), "CHANGE_EMAIL", 7));
    }
    @Test void lockedAccountsCannotFinishPendingEmailChange() {
        Challenge change = profileChange(); String code = otp(TARGET); user.setStatus("Locked");
        status(400, () -> service.verifyEmailChange(proof(change, code), "CHANGE_EMAIL", 7));
    }
    @Test void unverifiedAccountCannotUseProfilePathAndVerifiedAccountCannotUseLoginGrantPath() {
        status(400, () -> service.requestProfileEmailChange(7, new ProfileEmailChangeRequest(TARGET, "Password1!")));
        Challenge login = service.loginChallenge(user); user.setEmailVerified(true);
        status(400, () -> service.requestVerificationEmailChange(new VerificationEmailChangeRequest(ORIGINAL, login.verificationToken(), TARGET)));
    }
}
