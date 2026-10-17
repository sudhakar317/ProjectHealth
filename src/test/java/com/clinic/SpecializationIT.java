package com.clinic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import com.clinic.user.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class SpecializationIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin@clinic.example";
    private static final String DOCTOR = "doc@clinic.example";

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void seed() {
        users.save(new AppUser(ADMIN, Role.ADMIN, true));
        users.save(new AppUser(DOCTOR, Role.DOCTOR, true));
    }

    private static RequestPostProcessor as(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email));
    }

    private static MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder builder, String content) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private long create(String title) throws Exception {
        String response = mvc.perform(body(post("/api/admin/specializations"),
                        "{\"title\":\"%s\",\"description\":\"About %s\"}".formatted(title, title))
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/admin/specializations")).andExpect(status().isUnauthorized());
    }

    @Test
    void aDoctorCannotManageSpecializations() throws Exception {
        mvc.perform(get("/api/admin/specializations").with(as(DOCTOR))).andExpect(status().isForbidden());
        mvc.perform(body(post("/api/admin/specializations"), "{\"title\":\"X\",\"description\":\"Y\"}")
                        .with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theListIsAlphabetical() throws Exception {
        create("Neurology");
        create("Cardiology");

        mvc.perform(get("/api/admin/specializations").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Cardiology"))
                .andExpect(jsonPath("$[1].title").value("Neurology"));
    }

    @Test
    void aTitleIsUniqueRegardlessOfCase() throws Exception {
        create("Cardiology");

        mvc.perform(body(post("/api/admin/specializations"), "{\"title\":\"  CARDIOLOGY \",\"description\":\"Dup\"}")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.title").value("A specialization with this title already exists"));
    }

    @Test
    void renamingToItsOwnTitleIsNotAConflict() throws Exception {
        long id = create("Cardiology");

        mvc.perform(body(put("/api/admin/specializations/" + id),
                        "{\"title\":\"cardiology\",\"description\":\"Updated\"}").with(as(ADMIN)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("cardiology"));
    }

    @Test
    void aSpecializationInUseCannotBeDeleted() throws Exception {
        long id = create("Cardiology");
        mvc.perform(body(post("/api/admin/doctors"),
                        "{\"fullName\":\"Dr Meera\",\"email\":\"meera@clinic.example\",\"specializationId\":%d}"
                                .formatted(id)).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/admin/specializations/" + id).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This specialization is assigned to doctors and cannot be deleted"));
    }

    @Test
    void anUnusedSpecializationIsDeleted() throws Exception {
        long id = create("Cardiology");

        mvc.perform(delete("/api/admin/specializations/" + id).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/specializations").with(as(ADMIN)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void blankFieldsAreReportedPerField() throws Exception {
        mvc.perform(body(post("/api/admin/specializations"), "{\"title\":\" \",\"description\":\"\"}")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }
}
