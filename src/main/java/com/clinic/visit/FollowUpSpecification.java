package com.clinic.visit;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Visits whose follow-up has come due and nobody has answered yet, limited to patients the caller may see. */
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

            // Once a visit answers this booking the reminder is done. Deleted answers do not count,
            // so removing the visit that answered it puts the follow-up back on the list.
            Subquery<Long> answered = query.subquery(Long.class);
            Root<Visit> answer = answered.from(Visit.class);
            answered.select(answer.get("id"));
            answered.where(cb.equal(answer.get("previousVisit").get("id"), root.get("id")),
                    cb.isNull(answer.get("deletedAt")));
            predicates.add(cb.not(cb.exists(answered)));

            if (doctorId != null) {
                predicates.add(cb.equal(root.get("patient").get("doctor").get("id"), doctorId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
