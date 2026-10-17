package com.clinic.auth;

import com.clinic.audit.AuditService;
import com.clinic.common.ApiExceptionHandler;
import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import com.clinic.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEVELOPMENT / TESTING ONLY: signs in by email alone (no password, no Google). Only registered when
 * app.dev-login.enabled=true, which is set in the git-ignored application-local.properties and nowhere else.
 * Only active ADMIN or DOCTOR accounts in app_user are let in. The session it creates looks like a Google
 * session, so the rest of the app (SessionSyncFilter, /api/me, role checks) works unchanged.
 */
@RestController
@ConditionalOnProperty(name = "app.dev-login.enabled", havingValue = "true")
public class DevLoginController {

    public record DevLoginRequest(@NotBlank @Email String email) {
    }

    private final AppUserRepository users;
    private final AuditService audit;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public DevLoginController(AppUserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @PostMapping("/api/dev/login")
    public ResponseEntity<?> login(@Valid @RequestBody DevLoginRequest body, HttpServletRequest request,
                                     HttpServletResponse response) {
        String email = body.email().trim().toLowerCase();
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null || !user.isActive() || (user.getRole() != Role.ADMIN && user.getRole() != Role.DOCTOR)) {
            audit.record(AuditService.LOGIN_FAILURE, email, "dev_login_not_allowed", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiExceptionHandler.ApiError("This email is not registered as an admin or doctor", Map.of()));
        }

        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("dev-token", now, now.plusSeconds(8 * 3600),
                Map.of("sub", "dev:" + email, "email", email, "iss", "dev-login"));
        DefaultOidcUser principal = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())), idToken, "email");

        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "dev"));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        audit.record(AuditService.LOGIN_SUCCESS, email, "dev_login", request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("email", user.getEmail(), "role", user.getRole().name()));
    }
}
