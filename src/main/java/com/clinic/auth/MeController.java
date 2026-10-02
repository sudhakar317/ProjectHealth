package com.clinic.auth;

import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MeController {

    public record MeResponse(String email, String role) {
    }

    private final AppUserRepository users;

    public MeController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal OidcUser principal) {
        // Re-check the DB so a user disabled mid-session loses access immediately.
        AppUser user = users.findByEmail(principal.getEmail().toLowerCase())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new MeResponse(user.getEmail(), user.getRole().name());
    }
}
