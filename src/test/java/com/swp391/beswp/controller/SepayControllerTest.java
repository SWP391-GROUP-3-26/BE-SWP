package com.swp391.beswp.controller;

import com.swp391.beswp.config.*;
import com.swp391.beswp.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SepayController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SepayControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SepayService sepay;
    @MockitoBean JwtService jwt;
    @MockitoBean GoogleOAuth2SuccessHandler googleSuccess;
    @MockitoBean GoogleOAuth2FailureHandler googleFailure;
    @MockitoBean ClientRegistrationRepository clients;
    static final String BODY = """
        {"id":12345,"gateway":"MBBank","accountNumber":"0123456789","code":"SWP105",
         "content":"SWP105","transferType":"in","transferAmount":100000,"referenceCode":"REF"}
        """;

    @Test
    void webhookAcceptsProviderKeyWithoutMemberLoginAndReturnsRequiredAcknowledgement() throws Exception {
        mvc.perform(post("/api/payments/sepay/webhook").header("Authorization", "Apikey test-secret")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(sepay).processWebhook(eq("Apikey test-secret"), argThat(event -> event.id() == 12345L));
        verifyNoInteractions(jwt);
    }

    @Test
    void missingOrWrongKeyDoesNotReceiveSuccessAcknowledgement() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook SePay không hợp lệ"))
                .when(sepay).processWebhook(any(), any());
        mvc.perform(post("/api/payments/sepay/webhook").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/api/payments/sepay/webhook").header("Authorization", "Apikey wrong")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPayloadIsRejectedAndTransientDatabaseFailuresAreRetryable() throws Exception {
        mvc.perform(post("/api/payments/sepay/webhook").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sepay);
        doThrow(new org.springframework.transaction.TransactionSystemException("rollback"))
                .when(sepay).processWebhook(any(), any());
        mvc.perform(post("/api/payments/sepay/webhook").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is5xxServerError()).andExpect(jsonPath("$.success").value(false));
    }
}
