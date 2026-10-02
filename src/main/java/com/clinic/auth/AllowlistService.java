package com.clinic.auth;

import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AllowlistService {

    private final AppUserRepository users;

    public AllowlistService(AppUserRepository users) {
        this.users = users;
    }

    /** Returns the registered user, binding google_sub on first login, or throws AllowlistException. */
    @Transactional(noRollbackFor = AllowlistException.class)
    public AppUser authorize(String email, boolean emailVerified, String googleSub) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (!emailVerified) {
            throw new AllowlistException(AllowlistException.Reason.UNVERIFIED_EMAIL, normalized);
        }
        AppUser user = users.findByEmail(normalized)
                .orElseThrow(() -> new AllowlistException(AllowlistException.Reason.NOT_REGISTERED, normalized));
        if (!user.isActive()) {
            throw new AllowlistException(AllowlistException.Reason.INACTIVE, normalized);
        }
        if (user.getGoogleSub() == null) {
            user.setGoogleSub(googleSub);
        } else if (!user.getGoogleSub().equals(googleSub)) {
            throw new AllowlistException(AllowlistException.Reason.SUB_MISMATCH, normalized);
        }
        return user;
    }
}
