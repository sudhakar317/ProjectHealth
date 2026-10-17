package com.clinic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinic.audit.AuditService;
import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import com.clinic.user.Role;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class AuditLogIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin@clinic.example";
    private static final String DOCTOR = "doc@clinic.example";

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    AuditService audit;

    @BeforeEach
    void seed() {
        users.save(new AppUser(ADMIN, Role.ADMIN, true));
        users.save(new AppUser(DOCTOR, Role.DOCTOR, true));
        audit.record(AuditService.LOGIN_SUCCESS, DOCTOR, null, "10.0.0.7");
        audit.record(AuditService.LOGIN_FAILURE, "stranger@example.com", "not_registered", "10.0.0.8");
        audit.record(AuditService.PATIENT_CREATED, DOCTOR, "id=1", "10.0.0.7");
    }

    private static RequestPostProcessor as(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/admin/audit-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    void aDoctorCannotReadTheAuditLog() throws Exception {
        mvc.perform(get("/api/admin/audit-logs").with(as(DOCTOR))).andExpect(status().isForbidden());
    }

    @Test
    void anAdminSeesTheNewestEntryFirst() throws Exception {
        mvc.perform(get("/api/admin/audit-logs").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].eventType").value("PATIENT_CREATED"));
    }

    @Test
    void theIpAddressIsRecordedButNeverReturned() throws Exception {
        mvc.perform(get("/api/admin/audit-logs").with(as(ADMIN)))
                .andExpect(jsonPath("$.items[0].ipAddress").doesNotExist());
    }

    @Test
    void entriesFilterByEventType() throws Exception {
        mvc.perform(get("/api/admin/audit-logs").param("eventType", "LOGIN_FAILURE").with(as(ADMIN)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].actorEmail").value("stranger@example.com"));
    }

    @Test
    void entriesFilterByAnInclusiveDateRange() throws Exception {
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        // "to" covers the whole of its day, so today..today finds everything written today.
        mvc.perform(get("/api/admin/audit-logs").param("from", today).param("to", today).with(as(ADMIN)))
                .andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get("/api/admin/audit-logs").param("to", yesterday).with(as(ADMIN)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void anOversizedPageRequestIsCapped() throws Exception {
        mvc.perform(get("/api/admin/audit-logs").param("size", "100000").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }
}
