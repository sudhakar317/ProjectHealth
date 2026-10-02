package com.clinic.auth;

import com.clinic.audit.AuditService;
import com.clinic.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Redirects go to the frontend origin: the OAuth callback lands on the backend port, which serves no pages. */
@Component
public class LoginAuditHandlers {

    private final AuditService audit;
    private final String frontendUrl;

    public LoginAuditHandlers(AuditService audit, AppProperties props) {
        this.audit = audit;
        this.frontendUrl = props.frontendUrl();
    }

    public AuthenticationSuccessHandler successHandler() {
        SimpleUrlAuthenticationSuccessHandler redirect = new SimpleUrlAuthenticationSuccessHandler(frontendUrl + "/");
        redirect.setAlwaysUseDefaultTargetUrl(true);
        return (HttpServletRequest request, HttpServletResponse response, Authentication auth) -> {
            String email = auth.getPrincipal() instanceof OidcUser u ? u.getEmail() : auth.getName();
            audit.record(AuditService.LOGIN_SUCCESS, email, null, request.getRemoteAddr());
            redirect.onAuthenticationSuccess(request, response, auth);
        };
    }

    public AuthenticationFailureHandler failureHandler() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) -> {
            String email = null;
            String reason = "oauth2_error";
            if (ex instanceof AllowlistException a) {
                email = a.getEmail();
                reason = a.getReason().name().toLowerCase();
            }
            audit.record(AuditService.LOGIN_FAILURE, email, reason, request.getRemoteAddr());
            sendRedirect(response, frontendUrl + "/?error=" + URLEncoder.encode(reason, StandardCharsets.UTF_8));
        };
    }

    private void sendRedirect(HttpServletResponse response, String location) throws IOException {
        response.sendRedirect(location);
    }
}
