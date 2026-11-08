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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class RoomControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RoomService rooms;
    @MockitoBean JwtService jwt;
    @MockitoBean GoogleOAuth2SuccessHandler googleSuccess;
    @MockitoBean GoogleOAuth2FailureHandler googleFailure;
    @MockitoBean ClientRegistrationRepository clients;

    @ParameterizedTest @ValueSource(strings = {"Admin", "Center Manager"})
    void managersCanReadOptions(String role) throws Exception {
        mvc.perform(get("/api/rooms/options").with(user("manager").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(rooms).options();
    }
    @ParameterizedTest @ValueSource(strings = {"Member", "Coach", "Receptionist"})
    void otherRolesCannotManageRooms(String role) throws Exception {
        mvc.perform(get("/api/rooms").with(user("other").roles(role))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/rooms/1").with(user("other").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(rooms);
    }
    @Test void invalidTypeFilterIsBadRequest() throws Exception {
        mvc.perform(get("/api/rooms").param("type", "FREE_TEXT").with(user("admin").roles("Admin")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rooms);
    }
    @Test void invalidRoomBodyRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/rooms").with(user("admin").roles("Admin"))
                .contentType("application/json").content("""
                {"name":"Room","type":"MULTIPURPOSE","maxCapacity":0,"location":"","status":"Wrong"}
                """)).andExpect(status().isBadRequest());
        verifyNoInteractions(rooms);
    }

    @Test void removedYogaTypeIsRejectedInBodyAndFilter() throws Exception {
        mvc.perform(post("/api/rooms").with(user("admin").roles("Admin"))
                .contentType("application/json").content("""
                {"name":"Room","type":"YOGA","maxCapacity":20,"location":"Floor 1","status":"Active"}
                """)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/rooms").param("type", "YOGA").with(user("admin").roles("Admin")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rooms);
    }
}
