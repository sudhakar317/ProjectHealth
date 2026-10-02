package com.clinic.user;

import com.clinic.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All role/active changes must go through here so the hardcoded admin stays ADMIN and active.
 */
@Service
public class AppUserService {

    private final AppUserRepository users;
    private final AppProperties props;

    public AppUserService(AppUserRepository users, AppProperties props) {
        this.users = users;
        this.props = props;
    }

    @Transactional
    public AppUser changeRole(Long id, Role role) {
        AppUser user = users.findById(id).orElseThrow();
        if (isProtectedAdmin(user) && role != Role.ADMIN) {
            throw new ProtectedAdminException("The configured admin cannot be demoted");
        }
        user.setRole(role);
        return user;
    }

    @Transactional
    public AppUser setActive(Long id, boolean active) {
        AppUser user = users.findById(id).orElseThrow();
        if (isProtectedAdmin(user) && !active) {
            throw new ProtectedAdminException("The configured admin cannot be disabled");
        }
        user.setActive(active);
        return user;
    }

    /** Creates the admin if missing, and forces ADMIN + active if it exists. */
    @Transactional
    public AppUser ensureAdmin() {
        String email = props.normalizedAdminEmail();
        AppUser admin = users.findByEmail(email).orElseGet(() -> new AppUser(email, Role.ADMIN, true));
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        return users.save(admin);
    }

    private boolean isProtectedAdmin(AppUser user) {
        return user.getEmail().equalsIgnoreCase(props.normalizedAdminEmail());
    }
}
