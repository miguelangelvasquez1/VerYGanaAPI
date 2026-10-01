package com.verygana2.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.verygana2.models.User;
import com.verygana2.models.enums.AccountStatus;

public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final boolean passwordConfigured;
    private final AccountStatus accountStatus;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.passwordConfigured = user.isPasswordConfigured();
        this.accountStatus = user.getAccountStatus();
        this.authorities = authorities;
    }

    public Long getId() {
        return id;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public boolean isPasswordConfigured() {
        return passwordConfigured;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    // SUSPENDED conserva el comportamiento del antiguo BLOCKED. PREVENTIVELY_RESTRICTED
    // y TERMINATED son estados nuevos sin usuarios existentes: se deniega login por defecto.
    @Override
    public boolean isAccountNonLocked() {
        return accountStatus != AccountStatus.SUSPENDED
        && accountStatus != AccountStatus.PREVENTIVELY_RESTRICTED
        && accountStatus != AccountStatus.TERMINATED;
    }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    // Solo ACTIVE + password configurada habilita login. REGISTRATION_STARTED y
    // PENDING_ACCEPTANCE (sin caso de uso de login todavía) quedan deshabilitados
    // por defecto, igual que PENDING_VERIFICATION/PENDING_ACTIVATION.
    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE && passwordConfigured;
    }
}
