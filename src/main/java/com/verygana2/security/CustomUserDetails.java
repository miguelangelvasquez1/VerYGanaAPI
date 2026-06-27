package com.verygana2.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.verygana2.models.User;
import com.verygana2.models.enums.UserState;

public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
<<<<<<< HEAD
    private final boolean passwordConfigured;
=======
    private final UserState userState;
>>>>>>> 646e6eca78bf177337131e715bd2daa7764e36c8
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
<<<<<<< HEAD
        this.passwordConfigured = user.isPasswordConfigured();
=======
        this.userState = user.getUserState();
>>>>>>> 646e6eca78bf177337131e715bd2daa7764e36c8
        this.authorities = authorities;
    }

    public Long getId() {
        return id;
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

    @Override
    public boolean isAccountNonLocked() { return userState != UserState.BLOCKED; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
<<<<<<< HEAD
    public boolean isEnabled() { return passwordConfigured; }
=======
    public boolean isEnabled() {
        return userState != UserState.PENDING_EMAIL && userState != UserState.PENDING_KYC_REVIEW;
    }
>>>>>>> 646e6eca78bf177337131e715bd2daa7764e36c8
}