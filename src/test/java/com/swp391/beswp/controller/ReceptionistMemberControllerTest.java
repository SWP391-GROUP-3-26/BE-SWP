package com.swp391.beswp.controller;

import com.swp391.beswp.config.*;
import com.swp391.beswp.entity.Role;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.RoleRepository;
import com.swp391.beswp.repository.UserRepository;
import com.swp391.beswp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, ReceptionistMemberController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthService.class})
class ReceptionistMemberControllerTest {
    private static final String URL = "/api/receptionist/members";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean UserRepository users;
    @MockitoBean RoleRepository roles;
    @MockitoBean PasswordEncoder encoder;
    @MockitoBean JwtService jwt;
    @MockitoBean GoogleLoginExchangeService exchange;
    @MockitoBean GoogleOAuth2SuccessHandler googleSuccess;
    @MockitoBean GoogleOAuth2FailureHandler googleFailure;
    @MockitoBean ClientRegistrationRepository clients;
    private Map<String, String> body;

    @BeforeEach
    void setUp() {
        body = new LinkedHashMap<>(Map.of("username", " New.Member ", "fullName", " Nguyen Van A ",
                "email", " NEW@example.com ", "phone", "+84 (90) 123-4567",
                "password", "Password1!", "confirmPassword", "Password1!"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Coach", "Member", "Center Manager", "User"})
    void otherRolesCannotCreateMembers(String role) throws Exception {
        mvc.perform(post(URL).with(user("staff").roles(role)).contentType("application/json")
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Forbidden"));
        verifyNoInteractions(users, roles, encoder);
    }

    @Test
    void anonymousAndInvalidTokenCannotCreateMembers() throws Exception {
        for (String token : new String[]{"", "Bearer invalid"}) {
            if (!token.isEmpty()) when(jwt.validateToken("invalid")).thenThrow(new IllegalArgumentException());
            mvc.perform(post(URL).header("Authorization", token).contentType("application/json")
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        }
        verifyNoInteractions(users, roles, encoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Receptionist", "Member", "Admin", "Coach"})
    void bearerTokenUsesExistingRoleAuthority(String role) throws Exception {
        when(jwt.validateToken("valid-token")).thenReturn(Map.of("sub", "staff", "role", role));
        body.remove("username");
        mvc.perform(post(URL).header("Authorization", "Bearer valid-token")
                        .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().is(role.equals("Receptionist") ? 400 : 403))
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(users, roles, encoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Coach", "Receptionist"})
    void receptionistAlwaysCreatesMemberEvenWithInjectedRole(String injectedRole) throws Exception {
        Role member = new Role();
        member.setRoleName("Member");
        when(roles.findByRoleNameIgnoreCase("Member")).thenReturn(Optional.of(member));
        when(encoder.encode("Password1!")).thenReturn("hashed-password");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            assertSame(member, saved.getRole());
            assertEquals("hashed-password", saved.getPassword());
            saved.setId(42);
            return saved;
        });
        body.put("role", injectedRole);
        body.put("roleId", "1");
        body.put("roleName", injectedRole);
        mvc.perform(post(URL).with(user("staff").roles("Receptionist")).contentType("application/json")
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.role").value("Member"))
                .andExpect(jsonPath("$.data.username").value("new.member"))
                .andExpect(jsonPath("$.data.email").value("new@example.com"))
                .andExpect(jsonPath("$.data.phone").value("0901234567"))
                .andExpect(jsonPath("$.data.status").value("Active"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void validationErrorsMatchSelfRegistration() throws Exception {
        Map<String, String[]> invalid = Map.of(
                "username", new String[]{"", "bad name", "a".repeat(51)},
                "fullName", new String[]{"", "a".repeat(101)},
                "email", new String[]{"", "invalid", "a".repeat(101) + "@example.com", "a".repeat(256)},
                "phone", new String[]{"", "0121234567", "090123", "1".repeat(33)},
                "password", new String[]{"", "Aa1!", "password1!", "PASSWORD1!", "Password!!", "Password12", "A".repeat(73)},
                "confirmPassword", new String[]{"", "Mismatch1!", "a".repeat(73)});
        for (var field : invalid.entrySet()) {
            String original = body.get(field.getKey());
            for (String value : field.getValue()) {
                body.put(field.getKey(), value);
                assertSameErrorForBothEndpoints(400);
            }
            body.remove(field.getKey());
            assertSameErrorForBothEndpoints(400);
            body.put(field.getKey(), original);
        }
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "email", "phone"})
    void duplicateErrorsMatchSelfRegistration(String field) throws Exception {
        switch (field) {
            case "username" -> when(users.existsByUsernameIgnoreCase("new.member")).thenReturn(true);
            case "email" -> when(users.existsByEmailIgnoreCase("new@example.com")).thenReturn(true);
            case "phone" -> when(users.existsByPhone("0901234567")).thenReturn(true);
        }
        assertSameErrorForBothEndpoints(409);
        verify(users, never()).save(any());
    }

    private void assertSameErrorForBothEndpoints(int statusCode) throws Exception {
        String json = mapper.writeValueAsString(body);
        String selfError = mvc.perform(post("/api/auth/register").contentType("application/json").content(json))
                .andExpect(status().is(statusCode)).andExpect(jsonPath("$.success").value(false))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post(URL).with(user("staff").roles("Receptionist")).contentType("application/json").content(json))
                .andExpect(status().is(statusCode)).andExpect(content().json(selfError));
    }
}
