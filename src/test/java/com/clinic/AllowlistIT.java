package com.clinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinic.audit.AuditLog;
import com.clinic.audit.AuditLogRepository;
import com.clinic.audit.AuditService;
import com.clinic.auth.AllowlistException;
import com.clinic.auth.AllowlistException.Reason;
import com.clinic.auth.AllowlistService;
import com.clinic.auth.LoginAuditHandlers;
import com.clinic.config.AppProperties;
import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import com.clinic.user.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.dao.DataAccessException;

class AllowlistIT extends AbstractIntegrationTest {

    @Autowired
    AllowlistService allowlist;

    @Autowired
    AppUserRepository users;

    @Autowired
    AuditLogRepository auditLogs;

    @Autowired
    LoginAuditHandlers handlers;

    @Autowired
    AppProperties props;

    @Test
    void registeredActiveVerifiedUserIsAllowedAndSubIsBound() {
        users.save(new AppUser("doc@clinic.example", Role.DOCTOR, true));

        AppUser result = allowlist.authorize("Doc@Clinic.example", true, "sub-1");

        assertThat(result.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(users.findByEmail("doc@clinic.example").orElseThrow().getGoogleSub()).isEqualTo("sub-1");
    }

    @Test
    void boundSubMustMatchOnLaterLogins() {
        users.save(new AppUser("doc@clinic.example", Role.DOCTOR, true));
        allowlist.authorize("doc@clinic.example", true, "sub-1");

        assertThat(allowlist.authorize("doc@clinic.example", true, "sub-1")).isNotNull();
        assertRejected("doc@clinic.example", true, "other-sub", Reason.SUB_MISMATCH);
    }

    @Test
    void unregisteredEmailIsRejected() {
        assertRejected("stranger@example.com", true, "sub-x", Reason.NOT_REGISTERED);
    }

    @Test
    void inactiveUserIsRejectedAndSubNotBound() {
        users.save(new AppUser("off@clinic.example", Role.DOCTOR, false));

        assertRejected("off@clinic.example", true, "sub-2", Reason.INACTIVE);
        assertThat(users.findByEmail("off@clinic.example").orElseThrow().getGoogleSub()).isNull();
    }

    @Test
    void unverifiedEmailIsRejectedEvenIfRegistered() {
        users.save(new AppUser("doc@clinic.example", Role.DOCTOR, true));

        assertRejected("doc@clinic.example", false, "sub-3", Reason.UNVERIFIED_EMAIL);
        assertThat(users.findByEmail("doc@clinic.example").orElseThrow().getGoogleSub()).isNull();
    }

    @Test
    void failureHandlerWritesAuditRowAndRedirects() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.failureHandler().onAuthenticationFailure(
                new MockHttpServletRequest(), response, new AllowlistException(Reason.NOT_REGISTERED, "x@example.com"));

        List<AuditLog> rows = auditLogs.findByEventTypeOrderByIdAsc(AuditService.LOGIN_FAILURE);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getActorEmail()).isEqualTo("x@example.com");
        assertThat(rows.get(0).getDetails()).isEqualTo("not_registered");
        // The OAuth callback lands on the backend port, which serves no pages, so the handler sends
        // the browser back to the frontend origin rather than a path on this one.
        assertThat(response.getRedirectedUrl()).isEqualTo(props.frontendUrl() + "/?error=not_registered");
    }

    @Test
    void auditLogIsAppendOnly() {
        auditLogs.save(new AuditLog(AuditService.LOGIN_SUCCESS, "a@example.com", null, null));

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET actor_email = 'b@example.com'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log"))
                .isInstanceOf(DataAccessException.class);
    }

    private void assertRejected(String email, boolean verified, String sub, Reason expected) {
        assertThatThrownBy(() -> allowlist.authorize(email, verified, sub))
                .isInstanceOf(AllowlistException.class)
                .extracting(e -> ((AllowlistException) e).getReason())
                .isEqualTo(expected);
    }
}
