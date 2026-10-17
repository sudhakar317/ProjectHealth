package com.clinic.patient;

import com.clinic.common.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rules live in SecurityConfig (only a doctor may POST) and in PatientService, which scopes a
 * doctor to their own patients and lets an admin see all of them.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PatientResponse> list(@RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "false") boolean includeInactive,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @AuthenticationPrincipal OidcUser principal) {
        return service.list(search, includeInactive, page, size, email(principal));
    }

    @GetMapping("/{id}")
    public PatientResponse get(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal,
                               HttpServletRequest http) {
        return service.get(id, email(principal), http.getRemoteAddr());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse create(@Valid @RequestBody CreatePatientRequest request,
                                  @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        return service.create(request, email(principal), http.getRemoteAddr());
    }

    @PutMapping("/{id}")
    public PatientResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePatientRequest request,
                                  @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        return service.update(id, request, email(principal), http.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        service.delete(id, email(principal), http.getRemoteAddr());
    }

    @PostMapping("/{id}/restore")
    public PatientResponse restore(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal,
                                   HttpServletRequest http) {
        return service.restore(id, email(principal), http.getRemoteAddr());
    }

    private static String email(OidcUser principal) {
        return principal.getEmail().toLowerCase();
    }
}
