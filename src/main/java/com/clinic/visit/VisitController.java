package com.clinic.visit;

import com.clinic.common.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rules live in SecurityConfig: only a doctor may write. Which visits a caller can reach is
 * decided in VisitService, which defers to the patient access rule.
 */
@RestController
public class VisitController {

    private final VisitService service;

    public VisitController(VisitService service) {
        this.service = service;
    }

    @GetMapping("/api/patients/{patientId}/visits")
    public List<VisitResponse> listForPatient(@PathVariable Long patientId,
                                              @RequestParam(defaultValue = "false") boolean includeInactive,
                                              @AuthenticationPrincipal OidcUser principal,
                                              HttpServletRequest http) {
        return service.listForPatient(patientId, includeInactive, email(principal), http.getRemoteAddr());
    }

    @PostMapping("/api/patients/{patientId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitResponse create(@PathVariable Long patientId, @Valid @RequestBody CreateVisitRequest request,
                                @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        return service.create(patientId, request, email(principal), http.getRemoteAddr());
    }

    @GetMapping("/api/visits")
    public PageResponse<VisitResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal OidcUser principal) {
        return service.list(search, from, to, includeInactive, page, size, email(principal));
    }

    @GetMapping("/api/visits/follow-ups")
    public List<VisitResponse> followUps(@AuthenticationPrincipal OidcUser principal) {
        return service.followUps(email(principal));
    }

    @GetMapping("/api/visits/{id}")
    public VisitResponse get(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal,
                             HttpServletRequest http) {
        return service.get(id, email(principal), http.getRemoteAddr());
    }

    @PutMapping("/api/visits/{id}")
    public VisitResponse update(@PathVariable Long id, @Valid @RequestBody UpdateVisitRequest request,
                                @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        return service.update(id, request, email(principal), http.getRemoteAddr());
    }

    @DeleteMapping("/api/visits/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        service.delete(id, email(principal), http.getRemoteAddr());
    }

    @PostMapping("/api/visits/{id}/restore")
    public VisitResponse restore(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal,
                                 HttpServletRequest http) {
        return service.restore(id, email(principal), http.getRemoteAddr());
    }

    private static String email(OidcUser principal) {
        return principal.getEmail().toLowerCase();
    }
}
