package com.Rifacel.controllers;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
public class UserController {

    @GetMapping
    public String getMethodName() {
        return "Hello, " +  "! You are authenticated.";
    }
    

    // @PostMapping("/login")
    // public ResponseEntity<?> login(@RequestBody AuthRequest request) {
    // try {
    // Authentication authentication = authManager.authenticate(
    // new UsernamePasswordAuthenticationToken(request.getEmail(),
    // request.getPassword())); -> Aquí se llama a UserDetailsService

    // UserDetails userDetails = (UserDetails) authentication.getPrincipal();
    // String token = jwtUtil.generarToken(userDetails);

    // return ResponseEntity.ok(new AuthResponse(token));
    // } catch (AuthenticationException ex) {
    // return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales
    // inválidas");
    // }
    // }

}
