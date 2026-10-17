package com.clinic.patient;

import com.clinic.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Soft-deleted only: {@code deletedAt} marks a patient inactive and nothing deletes the row.
 * The owning doctor is set at registration and can only ever be swapped for another doctor,
 * never cleared — see {@link #reassignTo(AppUser)}.
 */
@Entity
@Table(name = "patient")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sex sex;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    private String address;

    private String notes;

    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private AppUser doctor;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Patient() {
    }

    public Patient(String fullName, String phone, Sex sex, LocalDate dateOfBirth, String address, String notes,
                   AppUser doctor, String createdBy) {
        this.fullName = fullName;
        this.phone = phone;
        this.sex = sex;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.notes = notes;
        this.doctor = doctor;
        this.createdBy = createdBy;
    }

    public void update(String fullName, String phone, Sex sex, LocalDate dateOfBirth, String address, String notes) {
        this.fullName = fullName;
        this.phone = phone;
        this.sex = sex;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.notes = notes;
    }

    /** Only ever called with a doctor that the service has already validated; null is rejected. */
    public void reassignTo(AppUser newDoctor) {
        if (newDoctor == null) {
            throw new IllegalArgumentException("A patient must always have a doctor");
        }
        this.doctor = newDoctor;
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

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public Sex getSex() {
        return sex;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getAddress() {
        return address;
    }

    public String getNotes() {
        return notes;
    }

    public AppUser getDoctor() {
        return doctor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
