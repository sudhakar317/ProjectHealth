package com.clinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinic.config.RequestIdFilter;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The client reads one error shape. These pin that every way a request can fail, including the ones
 * that never reach a controller, answers with it, and that nothing internal leaks out.
 */
class ErrorContractIT extends AbstractIntegrationTest {

    private static final String DOCTOR = "doc@clinic.example";

    @Autowired
    MockMvc mvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void seed() {
        users.save(new AppUser(DOCTOR, Role.DOCTOR, true));
    }

    private static RequestPostProcessor as(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email));
    }

    @Test
    void anUnauthenticatedCallGetsJsonNotAnEmptyBody() throws Exception {
        MvcResult result = mvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Your session has ended. Please sign in again"))
                .andReturn();

        // The id in the body is the one in the header, which is the one in the log.
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("requestId").asText())
                .isNotBlank()
                .isEqualTo(result.getResponse().getHeader(RequestIdFilter.HEADER));
    }

    @Test
    void theWrongRoleGetsAJsonForbidden() throws Exception {
        mvc.perform(get("/api/admin/doctors").with(as(DOCTOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You don't have permission to do that"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void aMissingCsrfTokenIsExplainedAsAnExpiredSession() throws Exception {
        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content("{}").with(as(DOCTOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your session has expired. Refresh the page and try again"));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content("{not json")
                        .with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request could not be read. Check the values and try again"));
    }

    @Test
    void aNonNumericIdIsABadRequestNotAServerError() throws Exception {
        mvc.perform(get("/api/visits/abc").with(as(DOCTOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'id' has an invalid value"));
    }

    @Test
    void anUnknownRouteIsANotFoundInTheSameShape() throws Exception {
        mvc.perform(get("/api/no-such-thing").with(as(DOCTOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found"));
    }

    @Test
    void theWrongMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(post("/api/me").with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("That action is not supported here"));
    }

    @Test
    void validationReportsEachFieldSoTheFormCanShowItInline() throws Exception {
        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"phone\":\"x\",\"sex\":\"MALE\",\"dateOfBirth\":\"1990-01-01\"}")
                        .with(as(DOCTOR)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Some fields need attention"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
    }


    @Test
    void aSafeIncomingRequestIdIsReusedSoAProxyCanTraceThrough() throws Exception {
        mvc.perform(get("/api/health").header(RequestIdFilter.HEADER, "lb-7f3a9c21"))
                .andExpect(header().string(RequestIdFilter.HEADER, "lb-7f3a9c21"));
    }

    @Test
    void anUnsafeIncomingRequestIdIsReplacedBecauseItLandsInTheLogs() throws Exception {
        String forged = "x\nFAKE LOG LINE";
        String issued = mvc.perform(get("/api/health").header(RequestIdFilter.HEADER, forged))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

        assertThat(issued).isNotEqualTo(forged).matches("[a-f0-9]{12}");
    }

    @Test
    void healthIsOpenToAnUptimeCheckAndSaysOnlyUpOrDown() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
