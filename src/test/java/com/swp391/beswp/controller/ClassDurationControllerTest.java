package com.swp391.beswp.controller;

import com.swp391.beswp.config.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import com.swp391.beswp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClassController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, ClassService.class})
class ClassDurationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @MockitoBean ClassRepository classes;
    @MockitoBean ClassSessionRepository sessions;
    @MockitoBean SubjectRepository subjects;
    @MockitoBean UserRepository users;
    @MockitoBean RoomRepository rooms;
    @MockitoBean Clock paymentClock;
    @MockitoBean JwtService jwt;
    @MockitoBean GoogleOAuth2SuccessHandler googleSuccess;
    @MockitoBean GoogleOAuth2FailureHandler googleFailure;
    @MockitoBean ClientRegistrationRepository clients;
    private static final String BODY = """
            {"name":"Yoga","subjectId":1,"coachId":3,"roomId":1,"daysOfWeek":["Thứ 5","Thứ 3"],
             "slotId":1,"startTime":"07:30","endTime":"09:00","maxCapacity":10,"price":100000,
             "startDate":"2026-10-13","durationWeeks":%s,"endDate":"2099-12-31"}
            """;

    @BeforeEach
    void fixtures() {
        when(paymentClock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(paymentClock.instant()).thenReturn(Instant.parse("2026-10-13T01:00:00Z"));
        Role role = new Role(); role.setRoleName("Coach");
        User coach = new User(); coach.setId(3); coach.setRole(role);
        when(users.findByIdForUpdate(3)).thenReturn(Optional.of(coach));
        when(subjects.findById(1)).thenReturn(Optional.of(Subject.builder().id(1).name("Yoga").build()));
        when(rooms.findByIdForUpdate(1)).thenReturn(Optional.of(Room.builder().id(1).maxCapacity(20).name("Room").build()));
        when(classes.save(any())).thenAnswer(i -> { ClassEntity c = i.getArgument(0); c.setId(10); return c; });
    }

    @Test
    void computedEndDateIgnoresClientAndResponseHasCanonicalDays() throws Exception {
        mvc.perform(post("/api/classes").with(user("manager").roles("Center Manager"))
                .contentType("application/json").content(BODY.formatted("1")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.startDate").value("2026-10-13"))
                .andExpect(jsonPath("$.data.durationWeeks").value(1)).andExpect(jsonPath("$.data.endDate").value("2026-10-18"))
                .andExpect(jsonPath("$.data.daysOfWeek[0]").value("T3"))
                .andExpect(jsonPath("$.data.daysOfWeek[1]").value("T5"));
        verify(sessions).saveAll(argThat(rows -> {
            List<ClassSession> list = new ArrayList<>(); rows.forEach(list::add);
            return list.size() == 2 && list.getFirst().getDate().equals(LocalDate.of(2026, 10, 13))
                    && list.getLast().getDate().equals(LocalDate.of(2026, 10, 15));
        }));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0", "-1", "1.5", "2147483648"})
    void missingNonpositiveFractionalAndOverflowingWeeksReturn400(String weeks) throws Exception {
        mvc.perform(post("/api/classes").with(user("manager").roles("Center Manager"))
                .contentType("application/json").content(BODY.formatted(weeks)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verify(classes, never()).save(any()); verify(sessions, never()).saveAll(any());
    }

    @Test
    void missingStartDateOrWrongSelectedDayReturn400() throws Exception {
        for (String body : List.of(BODY.formatted("1").replace("\"2026-10-13\"", "null"),
                BODY.formatted("1").replace("2026-10-13", "2026-10-14"))) {
            mvc.perform(post("/api/classes").with(user("manager").roles("Center Manager"))
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verify(classes, never()).save(any());
    }

    @Test
    void memberCannotCreateClasses() throws Exception {
        mvc.perform(post("/api/classes").with(user("member").roles("Member"))
                .contentType("application/json").content(BODY.formatted("1"))).andExpect(status().isForbidden());
        verifyNoInteractions(classes, sessions);
    }

    @Test
    void memberCalendarContractUsesActualDateAndMinuteFormattedSessionTimes() throws Exception {
        var item = com.swp391.beswp.dto.MyClassItemResponse.builder().startDate(LocalDate.of(2026, 10, 13))
                .endDate(LocalDate.of(2026, 10, 18)).durationWeeks(1).sessionId(20).sessionDate(LocalDate.of(2026, 10, 15))
                .sessionStartTime(LocalTime.of(7, 30)).sessionEndTime(LocalTime.of(9, 0)).rawStatus("Booked").build();
        var result = json.readTree(json.writeValueAsString(item));
        org.junit.jupiter.api.Assertions.assertEquals("2026-10-15", result.get("sessionDate").asText());
        org.junit.jupiter.api.Assertions.assertEquals("07:30", result.get("sessionStartTime").asText());
        org.junit.jupiter.api.Assertions.assertEquals("09:00", result.get("sessionEndTime").asText());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.get("durationWeeks").asInt());
    }
}
