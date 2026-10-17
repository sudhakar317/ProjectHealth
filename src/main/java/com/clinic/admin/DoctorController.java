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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/doctors")
public class DoctorController {

    private final DoctorService service;

    public DoctorController(DoctorService service) {
        this.service = service;
    }

    @GetMapping
    public List<DoctorResponse> list(@RequestParam(required = false) String fullName,
                                     @RequestParam(required = false) String email,
                                     @RequestParam(required = false) String phone,
                                     @RequestParam(required = false) Long specializationId,
                                     @RequestParam(required = false) Boolean active) {
        return service.list(new DoctorFilter(fullName, email, phone, specializationId, active));
    }

    @GetMapping("/{id}")
    public DoctorResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorResponse create(@Valid @RequestBody CreateDoctorRequest request,
                                 @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        return service.create(request, principal.getEmail().toLowerCase(), http.getRemoteAddr());
    }

    @PutMapping("/{id}")
    public DoctorResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDoctorRequest request,
                                 @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        return service.update(id, request, principal.getEmail().toLowerCase(), http.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal OidcUser principal, HttpServletRequest http) {
        service.delete(id, principal.getEmail().toLowerCase(), http.getRemoteAddr());
    }
}
