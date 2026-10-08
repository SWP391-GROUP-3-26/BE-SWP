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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.util.List;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, ReceptionistMemberController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthService.class, MemberService.class})
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

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankSearchReturnsEmptyWithoutDatabaseQuery(String keyword) throws Exception {
        mvc.perform(get(URL).param("keyword", keyword).with(user("staff").roles("Receptionist")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.size").value(20));
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void searchTrimsOuterSpacesPreservesInnerSpacesAndMapsSafeProfiles(int count) throws Exception {
        User member = member();
        List<User> matches = java.util.stream.IntStream.range(0, count).mapToObj(i -> member).toList();
        when(users.searchMembers(eq("Nguyen  An"), any(Pageable.class))).thenAnswer(invocation -> {
            Pageable page = invocation.getArgument(1);
            assertEquals(0, page.getPageNumber());
            assertEquals(20, page.getPageSize());
            assertTrue(page.getSort().getOrderFor("id").isAscending());
            return new PageImpl<>(matches, page, count);
        });
        var response = mvc.perform(get(URL).param("keyword", "  Nguyen  An  ")
                        .with(user("staff").roles("Receptionist")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(count))
                .andExpect(jsonPath("$.data.length()").value(count));
        if (count > 0) response.andExpect(jsonPath("$.data[0].userId").value(42))
                .andExpect(jsonPath("$.data[0].phone").value("0900000000"))
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].accessToken").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?page=abc", "?size=2147483648", "/abc", "/2147483648"})
    void invalidSearchAndDetailParametersReturn400(String suffix) throws Exception {
        mvc.perform(get(URL + suffix).with(user("staff").roles("Receptionist")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(users);
    }

    @Test
    void detailMapsProfileWithoutAuthenticationDataAndMissingMemberReturns404() throws Exception {
        when(users.findByIdAndRoleRoleNameIgnoreCase(42, "Member")).thenReturn(Optional.of(member()));
        mvc.perform(get(URL + "/42").with(user("staff").roles("Receptionist")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.role").value("Member"))
                .andExpect(jsonPath("$.data.dob").value("2000-01-02"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
        mvc.perform(get(URL + "/99").with(user("staff").roles("Receptionist")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Member not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Receptionist", "Member", "Admin", "Coach", "Center Manager", "User"})
    void getEndpointsUseExistingJwtRolePolicy(String role) throws Exception {
        when(jwt.validateToken("valid-token")).thenReturn(Map.of("sub", "staff", "role", role));
        when(users.findByIdAndRoleRoleNameIgnoreCase(42, "Member")).thenReturn(Optional.of(member()));
        for (String path : new String[]{URL, URL + "/42"}) {
            mvc.perform(get(path).header("Authorization", "Bearer valid-token"))
                    .andExpect(status().is(role.equals("Receptionist") ? 200 : 403));
        }
        if (!role.equals("Receptionist")) verifyNoInteractions(users);
    }

    @Test
    void anonymousAndInvalidJwtCannotReadMembers() throws Exception {
        when(jwt.validateToken("invalid")).thenThrow(new IllegalArgumentException());
        for (String path : new String[]{URL, URL + "/42"}) {
            for (String token : new String[]{"", "Bearer invalid"}) {
                mvc.perform(get(path).header("Authorization", token))
                        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Unauthorized"));
            }
        }
        verifyNoInteractions(users);
    }

    private User member() {
        Role role = new Role();
        role.setRoleName("Member");
        User member = new User();
        member.setId(42);
        member.setRole(role);
        member.setFullName("Nguyen  An");
        member.setPhone("0900000000");
        member.setDob(java.time.LocalDate.of(2000, 1, 2));
        member.setPassword("must-never-be-serialized");
        return member;
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
