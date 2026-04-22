package com.fsss.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {
    private final String credentialView;

    public ApiKeyAuthenticationToken(String apiKey, boolean authenticated) {
        super(authenticated ? AuthorityUtils.createAuthorityList("ROLE_UPSTREAM") : AuthorityUtils.NO_AUTHORITIES);
        this.credentialView = authenticated ? "[PROTECTED]" : apiKey;
        setAuthenticated(authenticated);
    }

    @Override
    public Object getCredentials() {
        return credentialView;
    }

    @Override
    public Object getPrincipal() {
        return "upstream";
    }
}
