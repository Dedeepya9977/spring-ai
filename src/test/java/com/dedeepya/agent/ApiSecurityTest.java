package com.dedeepya.agent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityTest {
  @Autowired MockMvc mvc;

  @Test
  void anonymousCannotCreateSession() throws Exception {
    mvc.perform(post("/api/sessions")).andExpect(status().isUnauthorized());
  }

  @Test
  void developerCannotApprove() throws Exception {
    mvc.perform(
            post("/api/runs/00000000-0000-0000-0000-000000000001/decision")
                .with(httpBasic("developer", "local-only"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\":true,\"reason\":\"Looks acceptable\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void managementInternalsAreNotPublic() throws Exception {
    mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
  }

  @Test
  void wrongPasswordRejected() throws Exception {
    mvc.perform(post("/api/sessions").with(httpBasic("developer", "wrong")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownRequestFieldsRejected() throws Exception {
    mvc.perform(
            post("/api/runs/00000000-0000-0000-0000-000000000001/decision")
                .with(httpBasic("reviewer", "local-only"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"approve\":true,\"reason\":\"Looks acceptable\",\"amountPaise\":50000}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }
}
