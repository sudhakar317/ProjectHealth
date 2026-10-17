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

    private long doctorId;
    private long otherDoctorId;
    private long adminId;

    @BeforeEach
    void seedUsers() {
        doctorId = users.save(new AppUser(DOCTOR, Role.DOCTOR, true)).getId();
        otherDoctorId = users.save(new AppUser(OTHER_DOCTOR, Role.DOCTOR, true)).getId();
        adminId = users.save(new AppUser(ADMIN, Role.ADMIN, true)).getId();
    }

    private static RequestPostProcessor as(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email));
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder builder, String content) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private static String visitBody(LocalDate visitDate, LocalDate nextVisitDate) {
        return visitBody(visitDate, nextVisitDate, null);
    }

    /** A null {@code doctorId} is sent as an absent field, exactly as the client omits it. */
    private static String visitBody(LocalDate visitDate, LocalDate nextVisitDate, Long doctorId) {
        String next = nextVisitDate == null ? "null" : "\"" + nextVisitDate + "\"";
        String author = doctorId == null ? "" : ",\"doctorId\":" + doctorId;
        return """
                {"visitDate":"%s","notes":"Sore throat, advised rest","nextVisitDate":%s%s}"""
                .formatted(visitDate, next, author);
    }

    private static String visitBodyWithNotes(LocalDate visitDate, LocalDate nextVisitDate, String notes) {
        String next = nextVisitDate == null ? "null" : "\"" + nextVisitDate + "\"";
        return """
                {"visitDate":"%s","notes":"%s","nextVisitDate":%s}""".formatted(visitDate, notes, next);
    }

    /** Records a visit and hands back the parsed response, for assertions on the follow-up link. */
    private JsonNode record(long patientId, LocalDate visitDate, LocalDate nextVisitDate) throws Exception {
        String response = mvc.perform(body(post("/api/patients/" + patientId + "/visits"),
                        visitBody(visitDate, nextVisitDate, null)).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private int openFollowUps() throws Exception {
        String response = mvc.perform(get("/api/visits/follow-ups").with(as(DOCTOR)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).size();
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
    void adminCanReadAndWriteVisits() throws Exception {
        long patientId = createPatient();
        long visitId = createVisit(patientId, null);

        mvc.perform(get("/api/patients/" + patientId + "/visits").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(body(put("/api/visits/" + visitId), visitBody(LocalDate.now(), null))
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/visits/" + visitId).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminRecordsAVisitAgainstTheChosenDoctor() throws Exception {
        long patientId = createPatient();

        mvc.perform(body(post("/api/patients/" + patientId + "/visits"),
                        visitBody(LocalDate.now(), null, otherDoctorId)).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctorId").value(otherDoctorId));
    }

    @Test
    void adminVisitWithoutADoctorFallsBackToThePatientsOwnDoctor() throws Exception {
        long patientId = createPatient();

        mvc.perform(body(post("/api/patients/" + patientId + "/visits"), visitBody(LocalDate.now(), null))
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctorId").value(doctorId));
    }

    @Test
    void adminCannotAssignAVisitToSomeoneWhoIsNotAnActiveDoctor() throws Exception {
        long patientId = createPatient();

        mvc.perform(body(post("/api/patients/" + patientId + "/visits"),
                        visitBody(LocalDate.now(), null, adminId)).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.doctorId").exists());
    }

    @Test
    void adminMovesAnExistingVisitToAnotherDoctor() throws Exception {
        long patientId = createPatient();
        long visitId = createVisit(patientId, null);

        mvc.perform(body(put("/api/visits/" + visitId), visitBody(LocalDate.now(), null, otherDoctorId))
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(otherDoctorId));
    }

    @Test
    void aDoctorNeverChoosesTheAuthor() throws Exception {
        long patientId = createPatient();

        // On create the body is ignored outright: the signed-in doctor is always the author.
        String created = mvc.perform(body(post("/api/patients/" + patientId + "/visits"),
                        visitBody(LocalDate.now(), null, otherDoctorId)).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctorId").value(doctorId))
                .andReturn().getResponse().getContentAsString();

        // And on update it cannot be moved away either.
        long visitId = json.readTree(created).get("id").asLong();
        mvc.perform(body(put("/api/visits/" + visitId), visitBody(LocalDate.now(), null, otherDoctorId))
                        .with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(doctorId));
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
    void followUpOnTheSameDayAsTheVisitIsRejected() throws Exception {
        long patientId = createPatient();

        // On create: booking the follow-up for the day of the visit is not a follow-up.
        mvc.perform(body(post("/api/patients/" + patientId + "/visits"),
                        visitBody(LocalDate.now(), LocalDate.now())).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.nextVisitDate").exists());

        // And on edit, where the dates are still open because nothing has answered this visit.
        long visitId = createVisit(patientId, LocalDate.now().minusDays(2), null);
        mvc.perform(body(put("/api/visits/" + visitId),
                        visitBody(LocalDate.now().minusDays(2), LocalDate.now().minusDays(2)))
                        .with(as(DOCTOR)).with(csrf()))
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
    void followUpsListsDueBookingsNobodyHasKeptYet() throws Exception {
        // Overdue and unanswered, so it is due. Separate patients, because a second visit on the
        // same patient would answer the first booking and take it off the list.
        long overdue = createPatient();
        createVisit(overdue, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));
        long future = createPatient();
        createVisit(future, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));

        mvc.perform(get("/api/visits/follow-ups").with(as(DOCTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patientName").value("Asha Rao"));
    }

    @Test
    void answeringABookingTakesItOffTheFollowUpList() throws Exception {
        long patientId = createPatient();
        JsonNode booking = record(patientId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));
        assertThat(openFollowUps()).isEqualTo(1);

        JsonNode answer = record(patientId, LocalDate.now(), null);

        assertThat(answer.get("previousVisitId").asLong()).isEqualTo(booking.get("id").asLong());
        // The client subtracts these two to show "+3 days late"; the server never stores that number.
        assertThat(answer.get("expectedDate").asText()).isEqualTo(LocalDate.now().minusDays(3).toString());
        assertThat(openFollowUps()).isZero();
    }


    @Test
    void aVisitInsertedBetweenTwoOthersNeverStealsAnExistingLink() throws Exception {
        long patientId = createPatient();
        JsonNode booking = record(patientId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(7));
        JsonNode answer = record(patientId, LocalDate.now().minusDays(5), null);

        // Recorded last but dated between the other two: the booking is already answered, so this
        // links to nothing and the original pairing is left exactly as it was.
        JsonNode inserted = record(patientId, LocalDate.now().minusDays(8), null);

        assertThat(inserted.get("previousVisitId").isNull()).isTrue();
        assertThat(answer.get("previousVisitId").asLong()).isEqualTo(booking.get("id").asLong());
    }

    @Test
    void deletingTheAnsweringVisitPutsTheBookingBack() throws Exception {
        long patientId = createPatient();
        record(patientId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));
        JsonNode answer = record(patientId, LocalDate.now(), null);
        assertThat(openFollowUps()).isZero();

        mvc.perform(delete("/api/visits/" + answer.get("id").asLong()).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(openFollowUps()).isEqualTo(1);
    }

    @Test
    void restoringAVisitWhoseBookingWasTakenBringsItBackUnlinked() throws Exception {
        long patientId = createPatient();
        record(patientId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));
        JsonNode first = record(patientId, LocalDate.now().minusDays(2), null);

        mvc.perform(delete("/api/visits/" + first.get("id").asLong()).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isNoContent());
        record(patientId, LocalDate.now(), null);

        // Two visits may not answer the same booking, so the restored one gives up its link.
        mvc.perform(post("/api/visits/" + first.get("id").asLong() + "/restore").with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousVisitId").doesNotExist());
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

    @Test
    void aSecondVisitOnTheSameDateIsRejected() throws Exception {
        long patientId = createPatient();
        createVisit(patientId, null);

        mvc.perform(body(post("/api/patients/" + patientId + "/visits"), visitBody(LocalDate.now(), null))
                        .with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.visitDate").exists());
    }

    @Test
    void twoPatientsMayBothBeSeenOnTheSameDate() throws Exception {
        // The rule is one visit per patient per day, not one visit per day for the whole clinic.
        long first = createPatient();
        long second = createPatient();

        createVisit(first, LocalDate.now(), null);
        createVisit(second, LocalDate.now(), null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM visit WHERE visit_date = CURRENT_DATE", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void aDeletedVisitFreesItsDateAgain() throws Exception {
        long patientId = createPatient();
        long visitId = createVisit(patientId, null);
        mvc.perform(delete("/api/visits/" + visitId).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(body(post("/api/patients/" + patientId + "/visits"), visitBody(LocalDate.now(), null))
                        .with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void bothDatesAreFixedOnceALaterVisitHasAnsweredThisOne() throws Exception {
        long patientId = createPatient();
        JsonNode booking = record(patientId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));
        record(patientId, LocalDate.now(), null);
        long bookingId = booking.get("id").asLong();

        mvc.perform(body(put("/api/visits/" + bookingId),
                        visitBody(LocalDate.now().minusDays(9), LocalDate.now().minusDays(3)))
                        .with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.visitDate").exists());

        mvc.perform(body(put("/api/visits/" + bookingId),
                        visitBody(LocalDate.now().minusDays(10), LocalDate.now().minusDays(1)))
                        .with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.nextVisitDate").exists());
    }

    @Test
    void theNotesOfAnAnsweredVisitCanStillBeCorrected() throws Exception {
        long patientId = createPatient();
        JsonNode booking = record(patientId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));
        record(patientId, LocalDate.now(), null);

        mvc.perform(body(put("/api/visits/" + booking.get("id").asLong()),
                        visitBodyWithNotes(LocalDate.now().minusDays(10), LocalDate.now().minusDays(3),
                                "Corrected: tonsillitis, not pharyngitis"))
                        .with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Corrected: tonsillitis, not pharyngitis"));
    }

    @Test
    void theNewestVisitCannotMoveBehindTheOneItFollows() throws Exception {
        long patientId = createPatient();
        record(patientId, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3));
        JsonNode answer = record(patientId, LocalDate.now(), null);

        mvc.perform(body(put("/api/visits/" + answer.get("id").asLong()),
                        visitBody(LocalDate.now().minusDays(11), null)).with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.visitDate").exists());
    }

    @Test
    void openingAPatientsHistoryIsAuditedOnceWithTheIdOnly() throws Exception {
        long patientId = createPatient();
        createVisit(patientId, null);

        mvc.perform(get("/api/patients/" + patientId + "/visits").with(as(DOCTOR))).andExpect(status().isOk());

        // One row per opening, not one per visit shown, and no notes or names (rules.md 5.7, 5.8).
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE event_type = 'VISIT_HISTORY_VIEWED' AND details = ?",
                Integer.class, "patient=" + patientId)).isEqualTo(1);
    }

    @Test
    void viewingASingleVisitIsAudited() throws Exception {
        long patientId = createPatient();
        long visitId = createVisit(patientId, null);

        mvc.perform(get("/api/visits/" + visitId).with(as(DOCTOR))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE event_type = 'VISIT_VIEWED'",
                Integer.class)).isEqualTo(1);
    }
}
