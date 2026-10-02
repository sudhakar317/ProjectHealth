package com.clinic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import com.clinic.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class MeControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Test
    void unauthenticatedGets401NotRedirect() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void validUserGetsEmailAndRole() throws Exception {
        users.save(new AppUser("doc@clinic.example", Role.DOCTOR, true));

        mvc.perform(get("/api/me")
                        .with(oidcLogin().idToken(t -> t.claim("email", "doc@clinic.example"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("doc@clinic.example"))
                .andExpect(jsonPath("$.role").value("DOCTOR"));
    }

    @Test
    void userDisabledMidSessionGets401() throws Exception {
        users.save(new AppUser("gone@clinic.example", Role.DOCTOR, false));

        mvc.perform(get("/api/me")
                        .with(oidcLogin().idToken(t -> t.claim("email", "gone@clinic.example"))))
                .andExpect(status().isUnauthorized());
    }
}
