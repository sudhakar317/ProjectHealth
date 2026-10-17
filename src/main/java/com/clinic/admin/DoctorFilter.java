package com.clinic.admin;

import com.clinic.user.AppUser;
import com.clinic.user.Role;
import jakarta.persistence.criteria.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters over the same fields used in the add-doctor form. Text filters are case-insensitive "contains". */
public record DoctorFilter(String fullName, String email, String phone, Long specializationId, Boolean active) {

    Specification<AppUser> toSpecification() {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("role"), Role.DOCTOR));
            addContains(p, cb, root.get("fullName"), fullName);
            addContains(p, cb, root.get("email"), email);
            addContains(p, cb, root.get("phone"), phone);
            if (specializationId != null) {
                p.add(cb.equal(root.get("specialization").get("id"), specializationId));
            }
            if (active != null) {
                p.add(cb.equal(root.get("active"), active));
            }
            return cb.and(p.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private static void addContains(List<jakarta.persistence.criteria.Predicate> p,
                                    jakarta.persistence.criteria.CriteriaBuilder cb, Path<String> path,
                                    String value) {
        if (value != null && !value.isBlank()) {
            String escaped = value.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            p.add(cb.like(cb.lower(path), "%" + escaped + "%", '\\'));
        }
    }
}
