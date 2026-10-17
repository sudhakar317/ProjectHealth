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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class PatientIT extends AbstractIntegrationTest {

    private static final String DOCTOR = "doc@clinic.example";
    private static final String OTHER_DOCTOR = "other@clinic.example";
    private static final String ADMIN = "admin@clinic.example";

    private static final String BODY = """
            {"fullName":"Asha Rao","phone":"9876543210","sex":"FEMALE","dateOfBirth":"1990-05-20",
             "address":"12 Mill Road","notes":"none"}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void seedUsers() {
        users.save(new AppUser(DOCTOR, Role.DOCTOR, true));
        users.save(new AppUser(OTHER_DOCTOR, Role.DOCTOR, true));
        users.save(new AppUser(ADMIN, Role.ADMIN, true));
    }

    private static RequestPostProcessor as(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email));
    }

    private MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private long createPatientAs(String email) throws Exception {
        String response = mvc.perform(jsonBody(post("/api/patients"), BODY).with(as(email)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void doctorCreatesAndSeesOwnPatient() throws Exception {
        long id = createPatientAs(DOCTOR);

        mvc.perform(get("/api/patients").with(as(DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].doctorName").value(DOCTOR));
    }

    @Test
    void otherDoctorCannotSeeOrOpenIt() throws Exception {
        long id = createPatientAs(DOCTOR);

        mvc.perform(get("/api/patients").with(as(OTHER_DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        // 404 rather than 403, so the response does not confirm the record exists
        mvc.perform(get("/api/patients/" + id).with(as(OTHER_DOCTOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminSeesEveryPatientButCannotCreate() throws Exception {
        createPatientAs(DOCTOR);
        createPatientAs(OTHER_DOCTOR);

        mvc.perform(get("/api/patients").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));

        mvc.perform(jsonBody(post("/api/patients"), BODY).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteHidesThePatientAndRestoreBringsItBack() throws Exception {
        long id = createPatientAs(DOCTOR);

        mvc.perform(delete("/api/patients/" + id).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isNoContent());

        // the row still exists, it is only marked inactive
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient WHERE deleted_at IS NOT NULL", Integer.class))
                .isEqualTo(1);

        mvc.perform(get("/api/patients").with(as(DOCTOR)))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/patients").param("includeInactive", "true").with(as(DOCTOR)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].active").value(false));

        mvc.perform(post("/api/patients/" + id + "/restore").with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateWithoutDoctorIdKeepsTheDoctor() throws Exception {
        long id = createPatientAs(DOCTOR);
        long doctorId = users.findByEmail(DOCTOR).orElseThrow().getId();

        mvc.perform(jsonBody(put("/api/patients/" + id), BODY).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(doctorId));

        assertThat(jdbc.queryForObject("SELECT doctor_id FROM patient WHERE id = " + id, Long.class))
                .isEqualTo(doctorId);
    }

    @Test
    void adminCanReassignToAnotherDoctorButADoctorCannot() throws Exception {
        long id = createPatientAs(DOCTOR);
        long otherId = users.findByEmail(OTHER_DOCTOR).orElseThrow().getId();
        String reassign = BODY.replace("}", ",\"doctorId\":" + otherId + "}");

        // a doctor's own attempt to reassign is ignored
        mvc.perform(jsonBody(put("/api/patients/" + id), reassign).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(users.findByEmail(DOCTOR).orElseThrow().getId()));

        mvc.perform(jsonBody(put("/api/patients/" + id), reassign).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(otherId));
    }

    @Test
    void auditDetailsNeverCarryPatientData() throws Exception {
        createPatientAs(DOCTOR);

        JsonNode logs = json.readTree(mvc.perform(get("/api/admin/audit-logs").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(logs.toString()).doesNotContain("Asha Rao").doesNotContain("9876543210");
    }

    @Test
    void invalidPatientIsRejected() throws Exception {
        String noName = BODY.replace("\"fullName\":\"Asha Rao\"", "\"fullName\":\"\"");
        mvc.perform(jsonBody(post("/api/patients"), noName).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());
    }
}
