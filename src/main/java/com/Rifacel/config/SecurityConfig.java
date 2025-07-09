package com.Rifacel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        
        return http.
                    csrf(csrf -> csrf.disable()) //Enable when sessionManagement is enabled.
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated()) //The user should be authenticated for any request in the application.
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) //Spring Security will never create an HttpSession and it will never use it to obtain the Security Context.
                    .httpBasic(Customizer.withDefaults())
                    .build();
    }
}
