package com.clinic.visit;

import com.clinic.patient.Patient;
import com.clinic.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One consultation. Soft-deleted only, and the recording doctor is fixed at creation so that
 * reassigning the patient later never rewrites who wrote the note.
 */
@Entity
@Table(name = "visit")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private AppUser doctor;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(nullable = false)
    private String notes;

    @Column(name = "next_visit_date")
    private LocalDate nextVisitDate;

    /**
     * The earlier visit whose follow-up this one answers. Null for a walk-in, or for a visit
     * recorded before the clinic started booking follow-ups: those start a chain of their own.
     */
    @ManyToOne
    @JoinColumn(name = "previous_visit_id")
    private Visit previousVisit;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Visit() {
    }

    public Visit(Patient patient, AppUser doctor, LocalDate visitDate, String notes, LocalDate nextVisitDate,
                 String createdBy) {
        this.patient = patient;
        this.doctor = doctor;
        this.visitDate = visitDate;
        this.notes = notes;
        this.nextVisitDate = nextVisitDate;
        this.createdBy = createdBy;
    }

    public void update(LocalDate visitDate, String notes, LocalDate nextVisitDate) {
        this.visitDate = visitDate;
        this.notes = notes;
        this.nextVisitDate = nextVisitDate;
    }

    /**
     * Only an admin reassigns a visit, and only to a doctor the service has already validated.
     * Null is rejected: a visit must always keep an author.
     */
    public void reassignTo(AppUser newDoctor) {
        if (newDoctor == null) {
            throw new IllegalArgumentException("A visit must always have a doctor");
        }
        this.doctor = newDoctor;
    }

    /** Null unlinks, which is how a doctor says "this was not the scheduled follow-up". */
    public void linkTo(Visit previous) {
        this.previousVisit = previous;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public AppUser getDoctor() {
        return doctor;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDate getNextVisitDate() {
        return nextVisitDate;
    }

    public Visit getPreviousVisit() {
        return previousVisit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
