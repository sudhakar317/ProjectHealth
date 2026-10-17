package com.clinic.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    /** Counts deactivated patients too: they still reference the doctor and still hold history. */
    boolean existsByDoctorId(Long doctorId);
}
