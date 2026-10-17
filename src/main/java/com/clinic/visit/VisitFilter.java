package com.clinic.visit;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * {@code doctorId} is the scoping the service applies: set for a doctor (patients they own) and
 * null for an admin (all patients). {@code search} matches the patient's name.
 */
record VisitFilter(String search, LocalDate from, LocalDate to, boolean includeInactive, Long doctorId,
                   Long patientId) {

    Specification<Visit> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!includeInactive) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            }
            if (patientId != null) {
                predicates.add(cb.equal(root.get("patient").get("id"), patientId));
            }
            // A doctor sees visits for the patients they own, matching the patient list rule.
            if (doctorId != null) {
                predicates.add(cb.equal(root.get("patient").get("doctor").get("id"), doctorId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("visitDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("visitDate"), to));
            }
            if (search != null && !search.isBlank()) {
                String term = "%" + escape(search.trim().toLowerCase()) + "%";
                predicates.add(cb.like(cb.lower(root.get("patient").get("fullName")), term, '\\'));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
