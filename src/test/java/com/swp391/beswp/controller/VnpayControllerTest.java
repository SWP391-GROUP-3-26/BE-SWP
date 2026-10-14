package com.swp391.beswp.controller;

import com.swp391.beswp.config.*;
import com.swp391.beswp.dto.MemberPaymentResponse;
import com.swp391.beswp.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({VnpayController.class, MemberPaymentController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class VnpayControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MemberPaymentService payments;
    @MockitoBean JwtService jwt;
    @MockitoBean GoogleOAuth2SuccessHandler googleSuccess;
    @MockitoBean GoogleOAuth2FailureHandler googleFailure;
    @MockitoBean ClientRegistrationRepository clients;

    @Test
    void callbacksArePublicAndReturnUsesRedirectOnly() throws Exception {
        when(payments.processIpn(any())).thenReturn(Map.of("RspCode", "97", "Message", "Invalid signature"));
        mvc.perform(get("/api/payments/vnpay/ipn")).andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97")).andExpect(jsonPath("$.data").doesNotExist());
        when(payments.returnUrl(any())).thenReturn("http://localhost:5173/member/payment-result?paymentId=105");
        mvc.perform(get("/api/payments/vnpay/return")).andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/member/payment-result?paymentId=105"));
    }

    @Test
    void duplicateCallbackParametersAreRejectedAndTransactionErrorsAreRetryable() throws Exception {
        mvc.perform(get("/api/payments/vnpay/ipn").param("vnp_TxnRef", "105", "106"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("99"));
        verifyNoInteractions(payments);
        when(payments.processIpn(any())).thenThrow(new org.springframework.transaction.TransactionSystemException("rollback"));
        mvc.perform(get("/api/payments/vnpay/ipn")).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("99"));
        mvc.perform(get("/api/payments/vnpay/return").param("vnp_TxnRef", "105", "106"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyLoggedInMemberCanReadFlatPaymentResponse() throws Exception {
        mvc.perform(get("/api/member/payments/105")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"Admin", "Coach", "Receptionist"}) {
            mvc.perform(get("/api/member/payments/105").with(user("1").roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(payments);
        when(payments.getPayment("1", 105)).thenReturn(MemberPaymentResponse.builder().success(true).paymentId(105)
                .bookingId(42).paymentStatus("Pending").bookingStatus("Chờ thanh toán").paymentAmount(new BigDecimal("100000"))
                .expiresAt(OffsetDateTime.parse("2026-10-13T08:15:00+07:00")).build());
        mvc.perform(get("/api/member/payments/105").with(user("1").roles("Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentId").value(105))
                .andExpect(jsonPath("$.paymentStatus").value("Pending"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-13T08:15:00+07:00"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
