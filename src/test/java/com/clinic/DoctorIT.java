package com.clinic;

import static org.assertj.core.api.Assertions.assertThat;
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

class DoctorIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin@clinic.example";
    private static final String DOCTOR = "doc@clinic.example";

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    ObjectMapper json;

    private long specializationId;

    @BeforeEach
    void seed() throws Exception {
        users.save(new AppUser(ADMIN, Role.ADMIN, true));
        users.save(new AppUser(DOCTOR, Role.DOCTOR, true));
        String created = mvc.perform(body(post("/api/admin/specializations"),
                        "{\"title\":\"Cardiology\",\"description\":\"Heart\"}").with(as(ADMIN)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        specializationId = json.readTree(created).get("id").asLong();
    }

    private static RequestPostProcessor as(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email));
    }

    private static MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder builder, String content) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private String doctor(String email) {
        return """
                {"fullName":"Dr Meera Iyer","email":"%s","phone":"","specializationId":%d}"""
                .formatted(email, specializationId);
    }

    private long createDoctor(String email) throws Exception {
        String response = mvc.perform(body(post("/api/admin/doctors"), doctor(email)).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private int auditRows(String eventType) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE event_type = ?", Integer.class, eventType);
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/admin/doctors")).andExpect(status().isUnauthorized());
    }

    @Test
    void aDoctorCannotManageDoctors() throws Exception {
        mvc.perform(get("/api/admin/doctors").with(as(DOCTOR))).andExpect(status().isForbidden());
        mvc.perform(body(post("/api/admin/doctors"), doctor("new@clinic.example")).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminAddsAndEditsADoctor() throws Exception {
        long id = createDoctor("Meera@Clinic.example");

        mvc.perform(get("/api/admin/doctors/" + id).with(as(ADMIN)))
                .andExpect(status().isOk())
                // Emails are the login identity, so they are stored lowercased.
                .andExpect(jsonPath("$.email").value("meera@clinic.example"))
                .andExpect(jsonPath("$.specializationTitle").value("Cardiology"))
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(body(put("/api/admin/doctors/" + id),
                        "{\"fullName\":\"Dr Meera I.\",\"phone\":\"9876543210\",\"specializationId\":%d,\"active\":false}"
                                .formatted(specializationId)).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Dr Meera I."))
                .andExpect(jsonPath("$.active").value(false));

        assertThat(auditRows("DOCTOR_CREATED")).isEqualTo(1);
        assertThat(auditRows("DOCTOR_UPDATED")).isEqualTo(1);
    }

    @Test
    void anEmailAlreadyInUseIsRejectedOnTheField() throws Exception {
        createDoctor("meera@clinic.example");

        mvc.perform(body(post("/api/admin/doctors"), doctor("MEERA@clinic.example")).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.email").value("A user with this email already exists"));
    }

    @Test
    void invalidInputIsReportedPerField() throws Exception {
        mvc.perform(body(post("/api/admin/doctors"),
                        "{\"fullName\":\"\",\"email\":\"not-an-email\",\"specializationId\":null}")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.specializationId").exists());
    }

    @Test
    void anUnknownDoctorIsNotFound() throws Exception {
        mvc.perform(get("/api/admin/doctors/9999").with(as(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Doctor not found"));
    }

    @Test
    void aDoctorWithNoRecordsCanBeDeleted() throws Exception {
        long id = createDoctor("meera@clinic.example");

        mvc.perform(delete("/api/admin/doctors/" + id).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(users.findById(id)).isEmpty();
        assertThat(auditRows("DOCTOR_DELETED")).isEqualTo(1);
    }

    @Test
    void aDoctorWithPatientsCannotBeDeletedAndTheAuditLogDoesNotClaimTheyWere() throws Exception {
        long id = createDoctor("meera@clinic.example");
        mvc.perform(body(post("/api/patients"), """
                        {"fullName":"Asha Rao","phone":"9876543210","sex":"FEMALE","dateOfBirth":"1990-05-20"}""")
                        .with(as("meera@clinic.example")).with(csrf()))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/admin/doctors/" + id).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This doctor has patients or visit records, so they can't be deleted. Deactivate them instead"));

        assertThat(users.findById(id)).isPresent();
        // The audit row is written in its own transaction, so it must never be written for a delete
        // that did not happen.
        assertThat(auditRows("DOCTOR_DELETED")).isZero();
    }
}
