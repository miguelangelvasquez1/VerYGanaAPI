package com.VerYGana.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.VerYGana.models.User;
import com.VerYGana.repositories.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;
    

    @Override // Se llama siempre que alguien intenta autenticarse
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email or phone: " + identifier));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail()) // o user.getPhone()
                .password(user.getPassword())
                .build();
    }
}
