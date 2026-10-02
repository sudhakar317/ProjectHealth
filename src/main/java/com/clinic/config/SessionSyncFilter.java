package com.clinic.config;

import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps a live session in step with the user table, so an admin's changes apply without a re-login:
 * a disabled or deleted user is signed out on the next request, and a changed role takes effect immediately.
 */
public class SessionSyncFilter extends OncePerRequestFilter {

    private final AppUserRepository users;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public SessionSyncFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof OAuth2AuthenticationToken token && token.getPrincipal() instanceof OidcUser principal) {
            AppUser user = users.findByEmail(principal.getEmail().toLowerCase()).filter(AppUser::isActive).orElse(null);
            if (user == null) {
                signOut(request);
            } else {
                refreshRole(request, response, token, principal, user);
            }
        }
        chain.doFilter(request, response);
    }

    private void signOut(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private void refreshRole(HttpServletRequest request, HttpServletResponse response, OAuth2AuthenticationToken token,
                             OidcUser principal, AppUser user) {
        String expected = "ROLE_" + user.getRole().name();
        boolean current = token.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(expected));
        if (current) {
            return;
        }
        OidcUser refreshed = new DefaultOidcUser(List.of(new SimpleGrantedAuthority(expected)), principal.getIdToken(),
                principal.getUserInfo(), "email");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(refreshed, refreshed.getAuthorities(),
                token.getAuthorizedClientRegistrationId()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
