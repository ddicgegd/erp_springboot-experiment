package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.modules.iam.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomUserDetails implements UserDetails {
    private final String id;
    private final String username;
    private final String email;
    private final String password;
    private final String fineractClientId;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;

    public CustomUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
        this.id = user.getId() != null ? String.valueOf(user.getId()) : null;
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.fineractClientId = user.getFineractClientId();
        this.authorities = authorities;
        this.enabled = true;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getFineractClientId() { return fineractClientId; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }
}
