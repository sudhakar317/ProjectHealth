package com.clinic.auth;

import com.clinic.user.AppUser;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class ClinicOidcUserService extends OidcUserService {

    private final AllowlistService allowlist;

    public ClinicOidcUserService(AllowlistService allowlist) {
        this.allowlist = allowlist;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser google = super.loadUser(request);
        boolean verified = Boolean.TRUE.equals(google.getEmailVerified());
        AppUser user = allowlist.authorize(google.getEmail(), verified, google.getSubject());
        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                google.getIdToken(),
                google.getUserInfo(),
                "email");
    }
}
