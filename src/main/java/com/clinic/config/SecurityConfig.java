package com.clinic.config;

import com.clinic.audit.AuditService;
import com.clinic.auth.ClinicOidcUserService;
import com.clinic.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.auth.LoginAuditHandlers;
import com.clinic.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ClinicOidcUserService oidcUserService,
                                            LoginAuditHandlers handlers,
                                            AppUserRepository users,
                                            AuditService audit,
                                            ObjectMapper json) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
                        // For load balancers and uptime checks; reveals nothing beyond up or down.
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        // Exists only when app.dev-login.enabled=true (local profile); a 404 otherwise
                        .requestMatchers("/api/dev/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // An admin records visits on a doctor's behalf, so both roles write them;
                        // the service decides whose visit it is. This sits above the /api/patients
                        // rule, which would otherwise swallow the nested visit path.
                        .requestMatchers("/api/patients/*/visits").hasAnyRole("ADMIN", "DOCTOR")
                        // Only a doctor registers patients; an admin may read and edit but not create.
                        // The exact path keeps /api/patients/{id}/restore on the rule below.
                        .requestMatchers(HttpMethod.POST, "/api/patients").hasRole("DOCTOR")
                        .requestMatchers("/api/patients/**").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/api/visits/**").hasAnyRole("ADMIN", "DOCTOR")
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(ui -> ui.oidcUserService(oidcUserService))
                        .successHandler(handlers.successHandler())
                        .failureHandler(handlers.failureHandler()))
                // API calls get JSON in the same shape as every other error, instead of a redirect to
                // Google or an empty body. These run before any controller, so the advice cannot.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                (request, response, e) -> writeError(response, json, HttpStatus.UNAUTHORIZED,
                                        "Your session has ended. Please sign in again"),
                                new AntPathRequestMatcher("/api/**"))
                        .accessDeniedHandler((request, response, e) -> writeError(response, json, HttpStatus.FORBIDDEN,
                                // A stale CSRF token is almost always an expired session or a tab left
                                // open across a sign-out, not an attack, and a refresh fixes it.
                                e instanceof CsrfException
                                        ? "Your session has expired. Refresh the page and try again"
                                        : "You don't have permission to do that")))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterAfter(new SessionSyncFilter(users), AnonymousAuthenticationFilter.class)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                audit.record(AuditService.LOGOUT, authentication.getName(), null, request.getRemoteAddr());
                            }
                        })
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    /** Ahead of Spring Security, so even a request it rejects carries a request id. */
    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    private static void writeError(HttpServletResponse response, ObjectMapper json, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ApiError.of(message));
    }

    /** Spring Security 6 defers CSRF token creation; touching it makes the XSRF-TOKEN cookie get written. */
    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
