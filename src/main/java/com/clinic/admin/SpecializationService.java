package com.clinic.admin;

import com.clinic.audit.AuditService;
import com.clinic.common.ConflictException;
import com.clinic.common.NotFoundException;
import com.clinic.user.AppUserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpecializationService {

    private final SpecializationRepository specializations;
    private final AppUserRepository users;
    private final AuditService audit;

    public SpecializationService(SpecializationRepository specializations, AppUserRepository users,
                                 AuditService audit) {
        this.specializations = specializations;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SpecializationResponse> list() {
        return specializations.findAll(Sort.by("title").ascending()).stream()
                .map(SpecializationResponse::from).toList();
    }

    @Transactional
    public SpecializationResponse create(SpecializationRequest request, String actor, String ip) {
        String title = request.title().trim();
        ensureTitleFree(title, null);
        Specialization saved = specializations.save(new Specialization(title, request.description().trim(), actor));
        audit.record(AuditService.SPECIALIZATION_CREATED, actor, describe(saved), ip);
        return SpecializationResponse.from(saved);
    }

    @Transactional
    public SpecializationResponse update(Long id, SpecializationRequest request, String actor, String ip) {
        Specialization s = find(id);
        String title = request.title().trim();
        ensureTitleFree(title, id);
        s.update(title, request.description().trim());
        audit.record(AuditService.SPECIALIZATION_UPDATED, actor, describe(s), ip);
        return SpecializationResponse.from(s);
    }

    @Transactional
    public void delete(Long id, String actor, String ip) {
        Specialization s = find(id);
        if (users.existsBySpecializationId(id)) {
            throw new ConflictException("This specialization is assigned to doctors and cannot be deleted", null);
        }
        specializations.delete(s);
        audit.record(AuditService.SPECIALIZATION_DELETED, actor, describe(s), ip);
    }

    Specialization find(Long id) {
        return specializations.findById(id).orElseThrow(() -> new NotFoundException("Specialization not found"));
    }

    private void ensureTitleFree(String title, Long selfId) {
        specializations.findByTitleIgnoreCase(title).filter(s -> !s.getId().equals(selfId)).ifPresent(s -> {
            throw new ConflictException("A specialization with this title already exists", "title");
        });
    }

    private String describe(Specialization s) {
        return "id=" + s.getId() + " title=" + s.getTitle();
    }
}
