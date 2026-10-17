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
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class VisitIT extends AbstractIntegrationTest {

    private static final String DOCTOR = "doc@clinic.example";
    private static final String OTHER_DOCTOR = "other@clinic.example";
    private static final String ADMIN = "admin@clinic.example";

    private static final String PATIENT = """
            {"fullName":"Asha Rao","phone":"9876543210","sex":"FEMALE","dateOfBirth":"1990-05-20",
             "address":"","notes":""}""";

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

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder builder, String content) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private static String visitBody(LocalDate visitDate, LocalDate nextVisitDate) {
        String next = nextVisitDate == null ? "null" : "\"" + nextVisitDate + "\"";
        return """
                {"visitDate":"%s","notes":"Sore throat, advised rest","nextVisitDate":%s}"""
                .formatted(visitDate, next);
    }

    private long createPatient() throws Exception {
        String response = mvc.perform(body(post("/api/patients"), PATIENT).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private long createVisit(long patientId, LocalDate nextVisitDate) throws Exception {
        return createVisit(patientId, LocalDate.now(), nextVisitDate);
    }

    private long createVisit(long patientId, LocalDate visitDate, LocalDate nextVisitDate) throws Exception {
        String response = mvc.perform(body(post("/api/patients/" + patientId + "/visits"),
                        visitBody(visitDate, nextVisitDate)).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/visits")).andExpect(status().isUnauthorized());
    }

    @Test
    void doctorRecordsAVisitOnOwnPatient() throws Exception {
        long patientId = createPatient();
        long visitId = createVisit(patientId, LocalDate.now().plusDays(7));

        mvc.perform(get("/api/patients/" + patientId + "/visits").with(as(DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(visitId))
                .andExpect(jsonPath("$[0].patientName").value("Asha Rao"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void anotherDoctorCannotReachThoseVisits() throws Exception {
        long patientId = createPatient();
        createVisit(patientId, null);

        mvc.perform(get("/api/patients/" + patientId + "/visits").with(as(OTHER_DOCTOR)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/visits").with(as(OTHER_DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void adminCanReadButNotWrite() throws Exception {
        long patientId = createPatient();
        long visitId = createVisit(patientId, null);

        mvc.perform(get("/api/patients/" + patientId + "/visits").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(body(post("/api/patients/" + patientId + "/visits"), visitBody(LocalDate.now(), null))
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(body(put("/api/visits/" + visitId), visitBody(LocalDate.now(), null))
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/visits/" + visitId).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteHidesTheVisitAndRestoreBringsItBack() throws Exception {
        long patientId = createPatient();
        long visitId = createVisit(patientId, null);

        mvc.perform(delete("/api/visits/" + visitId).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit WHERE deleted_at IS NOT NULL", Integer.class))
                .isEqualTo(1);

        mvc.perform(get("/api/patients/" + patientId + "/visits").with(as(DOCTOR)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/patients/" + patientId + "/visits").param("includeInactive", "true").with(as(DOCTOR)))
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(post("/api/visits/" + visitId + "/restore").with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void followUpDateBeforeVisitDateIsRejected() throws Exception {
        long patientId = createPatient();

        mvc.perform(body(post("/api/patients/" + patientId + "/visits"),
                        visitBody(LocalDate.now(), LocalDate.now().minusDays(1))).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.nextVisitDate").exists());
    }

    @Test
    void visitCannotBeRecordedForAnInactivePatient() throws Exception {
        long patientId = createPatient();
        mvc.perform(delete("/api/patients/" + patientId).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(body(post("/api/patients/" + patientId + "/visits"), visitBody(LocalDate.now(), null))
                        .with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void followUpsListsDueAndOverdueVisits() throws Exception {
        long patientId = createPatient();
        // An overdue follow-up belongs to a past visit; a future one should not be listed.
        createVisit(patientId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));
        createVisit(patientId, LocalDate.now(), LocalDate.now().plusDays(30));

        mvc.perform(get("/api/visits/follow-ups").with(as(DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientName").value("Asha Rao"));
    }

    @Test
    void auditDetailsNeverCarryNotesOrPatientName() throws Exception {
        long patientId = createPatient();
        createVisit(patientId, null);

        String logs = mvc.perform(get("/api/admin/audit-logs").param("eventType", "VISIT_CREATED").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(logs).contains("VISIT_CREATED").doesNotContain("Asha Rao").doesNotContain("Sore throat");
    }

    @Test
    void blankNotesAreRejected() throws Exception {
        long patientId = createPatient();
        String blank = visitBody(LocalDate.now(), null).replace("Sore throat, advised rest", "");

        mvc.perform(body(post("/api/patients/" + patientId + "/visits"), blank).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.notes").exists());
    }
}
