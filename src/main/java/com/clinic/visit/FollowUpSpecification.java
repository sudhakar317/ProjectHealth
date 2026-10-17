package com.clinic.visit;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Visits whose follow-up has come due, limited to patients the caller may see. */
final class FollowUpSpecification {

    private FollowUpSpecification() {
    }

    static Specification<Visit> dueBy(LocalDate today, Long doctorId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.isNotNull(root.get("nextVisitDate")));
            predicates.add(cb.lessThanOrEqualTo(root.get("nextVisitDate"), today));
            // A follow-up for a deactivated patient is noise, not a reminder.
            predicates.add(cb.isNull(root.get("patient").get("deletedAt")));
            if (doctorId != null) {
                predicates.add(cb.equal(root.get("patient").get("doctor").get("id"), doctorId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
