package com.swp391.beswp.controller;

import com.swp391.beswp.config.*;
import com.swp391.beswp.dto.EmailOtpDtos.*;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, AccountIdentityController.class, UserEmailChangeController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class EmailOtpControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean EmailOtpService otp;
    @MockitoBean AccountIdentityService identities;
    @MockitoBean GoogleLoginExchangeService exchange;
    @MockitoBean JwtService jwt;
    @MockitoBean GoogleOAuth2SuccessHandler googleSuccess;
    @MockitoBean GoogleOAuth2FailureHandler googleFailure;
    @MockitoBean ClientRegistrationRepository clients;

    @Test void registrationReturnsAcceptedChallengeInsteadOfCreatingOrLoggingInAccount() throws Exception {
        User pending = new User(); when(auth.prepareRegistration(any())).thenReturn(pending);
        when(otp.startRegistration(pending)).thenReturn(new Challenge("member@example.com", "proof", Instant.now(), 300, 60));
        mvc.perform(post("/api/auth/register").contentType("application/json").content("""
                {"username":"member","fullName":"Member","email":"member@example.com","phone":"0901234567",
                "password":"Password1!","confirmPassword":"Password1!"}
                """)).andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.verificationToken").value("proof"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist()).andExpect(jsonPath("$.data.otp").doesNotExist());
        verify(auth, never()).register(any()); verifyNoInteractions(jwt);
    }
    @ParameterizedTest @ValueSource(strings = {"register/verify", "email-verification/verify", "forgot-password/verify"})
    void malformedOtpIsRejectedForEveryPurpose(String path) throws Exception {
        mvc.perform(post("/api/auth/" + path).contentType("application/json").content("""
                {"email":"member@example.com","verificationToken":"proof","otp":"12345"}
                """)).andExpect(status().isBadRequest()); verifyNoInteractions(otp);
    }
    @Test void forgotPasswordAndResetAreAvailableBeforeLoginButRequireValidatedPayload() throws Exception {
        when(otp.requestPasswordReset("member@example.com")).thenReturn(new Challenge("member@example.com", "proof", Instant.now(), 300, 60));
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verificationToken").value("proof"));
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isBadRequest()); verify(otp, never()).resetPassword(any());
    }
    @ParameterizedTest @ValueSource(strings = {"Member", "Receptionist", "Coach", "Center Manager"})
    void onlyAdminCanReviewUnknownAccountProviders(String role) throws Exception {
        mvc.perform(get("/api/admin/account-identities").with(user("other").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(identities);
    }
    @Test void adminCanReviewAccountProviders() throws Exception {
        mvc.perform(get("/api/admin/account-identities").with(user("admin").roles("Admin"))).andExpect(status().isOk());
        verify(identities).unresolved(0, 20);
    }

    @ParameterizedTest @ValueSource(strings = {"fake@example.invalid", "invalid-legacy-email"})
    void passwordAuthenticatedMemberCanRequestDifferentEmailWithoutBearerToken(String originalEmail) throws Exception {
        when(otp.requestVerificationEmailChange(any())).thenReturn(new Challenge("real@example.com", "new-proof", Instant.now(), 300, 60));
        mvc.perform(post("/api/auth/email-verification/change-email/request").contentType("application/json").content("""
                {"email":"%s","verificationToken":"login-proof","newEmail":"real@example.com"}
                """.formatted(originalEmail))).andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value("real@example.com"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(otp).requestVerificationEmailChange(new VerificationEmailChangeRequest(originalEmail, "login-proof", "real@example.com"));
    }
    @Test void differentEmailRequestCannotSkipLoginProof() throws Exception {
        mvc.perform(post("/api/auth/email-verification/change-email/request").contentType("application/json").content("""
                {"email":"fake@example.invalid","newEmail":"real@example.com"}
                """)).andExpect(status().isBadRequest()); verifyNoInteractions(otp);
    }
    @Test void profileEmailChangeNeedsAuthenticationAndBindsToPrincipalRatherThanBodyUserId() throws Exception {
        String body = """
                {"newEmail":"real@example.com","currentPassword":"Password1!","userId":999}
                """;
        mvc.perform(post("/api/users/me/email-change/request").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(otp);
        mvc.perform(post("/api/users/me/email-change/request").with(user("7").roles("Member"))
                .contentType("application/json").content(body)).andExpect(status().isOk());
        verify(otp).requestProfileEmailChange(7, new ProfileEmailChangeRequest("real@example.com", "Password1!"));
    }
    @Test void profileCannotRequestChangeWithoutCurrentPassword() throws Exception {
        mvc.perform(post("/api/users/me/email-change/request").with(user("7").roles("Member"))
                .contentType("application/json").content("{\"newEmail\":\"real@example.com\"}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(otp);
    }
    @Test void confirmationAndResendUseDistinctScopesForBothFlows() throws Exception {
        String body = """
                {"email":"real@example.com","verificationToken":"proof","otp":"003291"}
                """;
        User changed = new User();
        when(otp.verifyEmailChange(any(), eq("VERIFY_CHANGE_EMAIL"), isNull())).thenReturn(changed);
        when(otp.verifyEmailChange(any(), eq("CHANGE_EMAIL"), eq(7))).thenReturn(changed);
        mvc.perform(post("/api/auth/email-verification/change-email/verify").contentType("application/json").content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/users/me/email-change/verify").with(user("7").roles("Member"))
                .contentType("application/json").content(body)).andExpect(status().isOk());
        verify(auth, times(2)).authenticated(changed);
        mvc.perform(post("/api/auth/email-verification/change-email/resend").contentType("application/json").content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/users/me/email-change/resend").with(user("7").roles("Member"))
                .contentType("application/json").content(body)).andExpect(status().isOk());
        verify(otp).resendEmailChange(new ContextRequest("real@example.com", "proof"), "VERIFY_CHANGE_EMAIL", null);
        verify(otp).resendEmailChange(new ContextRequest("real@example.com", "proof"), "CHANGE_EMAIL", 7);
    }
}
