package com.clinic.user;

import com.clinic.admin.Specialization;
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

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "google_sub")
    private String googleSub;

    @Column(name = "full_name")
    private String fullName;

    private String phone;

    @ManyToOne
    @JoinColumn(name = "specialization_id")
    private Specialization specialization;

    /** No longer captured or shown. The column is kept so values recorded before it was dropped survive. */
    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(String email, Role role, boolean active) {
        this.email = email.trim().toLowerCase();
        this.role = role;
        this.active = active;
    }

    public static AppUser doctor(String email, String fullName, String phone, Specialization specialization,
                                 String createdBy) {
        AppUser user = new AppUser(email, Role.DOCTOR, true);
        user.fullName = fullName;
        user.phone = phone;
        user.specialization = specialization;
        user.createdBy = createdBy;
        return user;
    }

    public void updateDoctor(String fullName, String phone, Specialization specialization, boolean active) {
        this.fullName = fullName;
        this.phone = phone;
        this.specialization = specialization;
        this.active = active;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public Specialization getSpecialization() {
        return specialization;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public void setGoogleSub(String googleSub) {
        this.googleSub = googleSub;
    }
}
