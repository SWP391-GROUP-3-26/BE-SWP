package com.swp391.beswp.controller;

import com.swp391.beswp.config.*;
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
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReceptionistRegistrationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ReceptionistRegistrationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReceptionistRegistrationService registrations;
    @MockitoBean JwtService jwt;
    @MockitoBean GoogleOAuth2SuccessHandler googleSuccess;
    @MockitoBean GoogleOAuth2FailureHandler googleFailure;
    @MockitoBean ClientRegistrationRepository clients;
    final String base = "/api/receptionist/registrations";

    @ParameterizedTest @ValueSource(strings = {"Member", "Coach", "Center Manager", "Admin"})
    void otherRolesCannotReadRegisterCancelOrInspectPayments(String role) throws Exception {
        for (String path : new String[]{"/packages", "/classes", "/members/7/classes/11", "/members/7/payments/20"}) {
            mvc.perform(get(base + path).with(user("7").roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(post(base + "/members/7/packages").with(user("7").roles(role))
                .contentType("application/json").content("{\"packageId\":1,\"paymentMethod\":\"Cash\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/members/7/classes/bookings/42/cancel").with(user("7").roles(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(registrations);
    }

    @Test void anonymousCannotUseRegistrationEndpoints() throws Exception {
        mvc.perform(get(base + "/packages")).andExpect(status().isUnauthorized());
        verifyNoInteractions(registrations);
    }

    @Test void receptionistOnlineClassUsesPendingFlowAndNeverCounterRegistration() throws Exception {
        when(registrations.registerOnlineClass(any(), eq(7), any(), anyString())).thenReturn(Map.of("success", true, "paymentStatus", "Pending"));
        mvc.perform(post(base + "/members/7/classes").with(user("2").roles("Receptionist"))
                .contentType("application/json").content("{\"classId\":11,\"paymentMethod\":\"DIRECT\",\"paymentChannel\":\"Card\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paymentStatus").value("Pending"));
        verify(registrations, never()).registerClass(any(), any(), any());
    }

    @Test void receptionistOnlinePackageNeverUsesCashRegistration() throws Exception {
        when(registrations.registerOnlinePackage(any(), eq(7), any(), anyString())).thenReturn(Map.of("success", true, "paymentStatus", "Pending"));
        mvc.perform(post(base + "/members/7/packages").with(user("2").roles("Receptionist"))
                .contentType("application/json").content("{\"packageId\":1,\"paymentMethod\":\"E_Wallet\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paymentStatus").value("Pending"));
        verify(registrations, never()).registerPackage(any(), any(), any());
    }
}
