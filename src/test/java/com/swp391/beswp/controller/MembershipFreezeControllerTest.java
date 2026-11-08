package com.swp391.beswp.controller;

import com.swp391.beswp.service.MembershipFreezeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MembershipFreezeControllerTest {
    final MembershipFreezeService service = mock(MembershipFreezeService.class);
    final UsernamePasswordAuthenticationToken member = new UsernamePasswordAuthenticationToken("7", "");
    final String path = "/api/member/packages/subscriptions/10";
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new MembershipFreezeController(service)).build();
    }

    @Test void freezeResumeAndStateUseAuthenticatedOwner() throws Exception {
        mvc.perform(post(path + "/freeze").principal(member).contentType("application/json").content("{\"months\":2}"))
                .andExpect(status().isOk());
        mvc.perform(post(path + "/resume").principal(member)).andExpect(status().isOk());
        mvc.perform(get(path + "/freeze").principal(member)).andExpect(status().isOk());
        verify(service).freeze("7", 10, 2);
        verify(service).resume("7", 10);
        verify(service).state("7", 10);
    }

    @Test void invalidMonthCountDoesNotInvokeService() throws Exception {
        for (String body : new String[]{"{}", "{\"months\":0}", "{\"months\":-1}"}) {
            mvc.perform(post(path + "/freeze").principal(member).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
