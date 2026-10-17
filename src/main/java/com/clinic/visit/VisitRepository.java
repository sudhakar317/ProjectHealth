package com.clinic.visit;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitRepository extends JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {

    /**
     * Bookings this patient has not kept yet, most recent first, considering only visits recorded
     * on or before {@code onOrBefore}: a booking cannot be answered by a visit that predates it.
     * {@code excludeId} keeps the visit being edited from blocking its own re-link.
     */
    @Query("""
            SELECT v FROM Visit v
            WHERE v.patient.id = :patientId
              AND v.deletedAt IS NULL
              AND v.nextVisitDate IS NOT NULL
              AND v.visitDate <= :onOrBefore
              AND v.id <> :excludeId
              AND NOT EXISTS (
                  SELECT a.id FROM Visit a
                  WHERE a.previousVisit.id = v.id
                    AND a.deletedAt IS NULL
                    AND a.id <> :excludeId)
            ORDER BY v.visitDate DESC, v.id DESC
            """)
    List<Visit> findOpenFollowUps(@Param("patientId") Long patientId,
                                  @Param("onOrBefore") LocalDate onOrBefore,
                                  @Param("excludeId") Long excludeId,
                                  Pageable pageable);

    /** True when some other live visit already answers that booking. */
    boolean existsByPreviousVisitIdAndDeletedAtIsNullAndIdNot(Long previousVisitId, Long id);

    /** True once a later visit has answered this one, which freezes its dates. */
    boolean existsByPreviousVisitIdAndDeletedAtIsNull(Long previousVisitId);

    /** One visit per patient per day; a deleted visit frees its date again. */
    boolean existsByPatientIdAndVisitDateAndDeletedAtIsNull(Long patientId, LocalDate visitDate);

    boolean existsByPatientIdAndVisitDateAndDeletedAtIsNullAndIdNot(Long patientId, LocalDate visitDate, Long id);

    /** Includes deleted visits: they are still authored records and still reference the doctor. */
    boolean existsByDoctorId(Long doctorId);
}
