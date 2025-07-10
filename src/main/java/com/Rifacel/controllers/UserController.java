package com.Rifacel.controllers;

import java.net.URI;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Rifacel.models.User;
import com.Rifacel.security.TokenService;
import com.Rifacel.services.interfaces.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;
    private final TokenService tokenService;

    public UserController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping
    public String getMethodName() {
        return "Hello, " + "! You are authenticated.";
    }

    @PostMapping("/token")
    public String postMethodName(Authentication authorization) {
        String token = tokenService.generateToken(authorization);
        return token;
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

    // Registrar un usuario
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user) {
        User createdUser = userService.registerUser(user);
        return ResponseEntity.created(URI.create("/users/" + createdUser.getId())).body(createdUser);
    }

    // Obtener usuario por id
    @GetMapping("/id/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        User foundUser = userService.getUserById(id);
        return ResponseEntity.ok(foundUser);
    }

    // Obtener usuario por email
    @GetMapping("/email/{email}")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        User foundUser = userService.getUserByEmail(email);
        return ResponseEntity.ok(foundUser);
    }

    // Verificar si un email ya existe
    @GetMapping("/exists/email/{email}")
    public ResponseEntity<Boolean> emailExists(@PathVariable String email) {
        return ResponseEntity.ok(userService.emailExists(email));
    }

    // Verificar si un número de teléfono ya existe
    @GetMapping("/exists/phoneNumber/{phoneNumber}")
    public ResponseEntity<Boolean> phoneExists(@PathVariable String phoneNumber) {
        return ResponseEntity.ok(userService.phoneExists(phoneNumber));
    }
}
