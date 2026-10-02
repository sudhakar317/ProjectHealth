package com.clinic.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/specializations")
public class SpecializationController {

    private final SpecializationService service;

    public SpecializationController(SpecializationService service) {
        this.service = service;
    }

    @GetMapping
    public List<SpecializationResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpecializationResponse create(@Valid @RequestBody SpecializationRequest request,
                                         @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        return service.create(request, principal.getEmail().toLowerCase(), http.getRemoteAddr());
    }

    @PutMapping("/{id}")
    public SpecializationResponse update(@PathVariable Long id, @Valid @RequestBody SpecializationRequest request,
                                         @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        return service.update(id, request, principal.getEmail().toLowerCase(), http.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        service.delete(id, principal.getEmail().toLowerCase(), http.getRemoteAddr());
    }
}
