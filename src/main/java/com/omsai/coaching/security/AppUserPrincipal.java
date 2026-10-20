package com.omsai.coaching.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

/**
 * Authenticated principal exposing the account's id, display name and
 * role to controllers and views. Carries the stored BCrypt hash for
 * Spring Security's password check.
 */
public record AppUserPrincipal(Long id, String email, String fullName, UserAccount.Role role,
                               String passwordHash, boolean active)
        implements UserDetails {

    public static AppUserPrincipal from(UserAccount user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getPasswordHash(), user.isActive());
    }

    public boolean isAdmin() {
        return role == UserAccount.Role.ADMIN;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
