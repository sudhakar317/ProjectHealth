package com.clinic.auth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

public class AllowlistException extends OAuth2AuthenticationException {

    public enum Reason {
        UNVERIFIED_EMAIL,
        NOT_REGISTERED,
        INACTIVE,
        SUB_MISMATCH
    }

    private final Reason reason;
    private final String email;

    public AllowlistException(Reason reason, String email) {
        super(new OAuth2Error(reason.name().toLowerCase()), reason.name());
        this.reason = reason;
        this.email = email;
    }

    public Reason getReason() {
        return reason;
    }

    public String getEmail() {
        return email;
    }
}
