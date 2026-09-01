package com.dtcc.intern.demo.security;

import com.dtcc.intern.demo.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AuthenticatedUser implements UserDetails {

    private final Long userId;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final String role;
    private final Long teamId;

    public AuthenticatedUser(Long userId, String name, String email, String passwordHash, String role, Long teamId) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.teamId = teamId;
    }

    public static AuthenticatedUser from(AppUser user) {
        return new AuthenticatedUser(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().getRoleName().trim().toUpperCase(),
                user.getTeam() == null ? null : user.getTeam().getTeamId());
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public Long getTeamId() {
        return teamId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
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
        return true;
    }
}
