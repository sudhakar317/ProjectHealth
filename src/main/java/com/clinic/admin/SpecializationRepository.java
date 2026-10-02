package com.clinic.admin;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecializationRepository extends JpaRepository<Specialization, Long> {

    Optional<Specialization> findByTitleIgnoreCase(String title);
}
