package com.verygana2.security;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

import com.verygana2.exceptions.authExceptions.PasswordNotConfiguredException;
import com.verygana2.exceptions.authExceptions.PendingEmailVerificationException;
import com.verygana2.exceptions.authExceptions.PendingKycReviewException;
import com.verygana2.models.enums.UserState;

/**
 * Reemplaza el DefaultPreAuthenticationChecks de Spring Security para poder
 * distinguir, por login, cuál de las tres condiciones detrás de
 * CustomUserDetails.isEnabled() causó el rechazo (email sin verificar, KYC en
 * revisión o contraseña no configurada), en vez del DisabledException
 * genérico que no permite diferenciarlas en el front.
 */
public class CustomUserDetailsChecker implements UserDetailsChecker {

    @Override
    public void check(UserDetails user) {
        if (!user.isAccountNonLocked()) {
            throw new LockedException("User account is locked");
        }

        if (!user.isEnabled()) {
            if (user instanceof CustomUserDetails customUserDetails) {
                UserState state = customUserDetails.getUserState();

                if (state == UserState.PENDING_EMAIL) {
                    throw new PendingEmailVerificationException("Email not verified");
                }
                if (state == UserState.PENDING_KYC_REVIEW) {
                    throw new PendingKycReviewException("Account pending KYC review");
                }
                if (!customUserDetails.isPasswordConfigured()) {
                    throw new PasswordNotConfiguredException("Password not configured");
                }
            }
            throw new DisabledException("User is disabled");
        }

        if (!user.isAccountNonExpired()) {
            throw new AccountExpiredException("User account has expired");
        }
    }
}
