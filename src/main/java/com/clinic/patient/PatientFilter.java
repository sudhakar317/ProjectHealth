package com.clinic.patient;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * One search term matched against name or phone, plus the scoping the service applies:
 * {@code doctorId} is set for a doctor (their own patients only) and null for an admin (all).
 */
record PatientFilter(String search, boolean includeInactive, Long doctorId) {

    Specification<Patient> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!includeInactive) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            }
            if (doctorId != null) {
                predicates.add(cb.equal(root.get("doctor").get("id"), doctorId));
            }
            if (search != null && !search.isBlank()) {
                String term = "%" + escape(search.trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), term, '\\'),
                        cb.like(cb.lower(root.get("phone")), term, '\\')));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
