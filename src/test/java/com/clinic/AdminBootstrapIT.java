package com.clinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinic.user.AdminBootstrap;
import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import com.clinic.user.AppUserService;
import com.clinic.user.ProtectedAdminException;
import com.clinic.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminBootstrapIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin@test.example";

    @Autowired
    AdminBootstrap bootstrap;

    @Autowired
    AppUserService service;

    @Autowired
    AppUserRepository users;

    @Test
    void adminIsCreatedOnStartup() {
        bootstrap.run(null);

        AppUser admin = users.findByEmail(ADMIN).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();
    }

    @Test
    void existingDisabledOrDemotedAdminIsRestoredOnStartup() {
        users.save(new AppUser(ADMIN, Role.DOCTOR, false));

        bootstrap.run(null);

        AppUser admin = users.findByEmail(ADMIN).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();
    }

    @Test
    void adminCannotBeDisabledOrDemoted() {
        bootstrap.run(null);
        Long id = users.findByEmail(ADMIN).orElseThrow().getId();

        assertThatThrownBy(() -> service.setActive(id, false)).isInstanceOf(ProtectedAdminException.class);
        assertThatThrownBy(() -> service.changeRole(id, Role.DOCTOR)).isInstanceOf(ProtectedAdminException.class);
    }
}
