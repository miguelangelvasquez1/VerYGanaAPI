package com.VerYGana.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.VerYGana.models.User;
import com.VerYGana.services.interfaces.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    //Borrrar
    @GetMapping
    public ResponseEntity<String> getAllUsers() {
        return ResponseEntity.ok("Hello, Users! This endpoint is under construction.");
    }
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_ADMIN2')")
    public String me(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt.getSubject(); // el "sub"
        String scope = jwt.getClaim("scope"); // tu claim personalizado
        Long id = jwt.getClaim("userId");
        return "User: " + subject + ", Roles: " + scope + " Id: " + id;
    }

    // Obtener usuario por id
    @GetMapping("/id/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
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

    // Borrar un usuario por id
    @DeleteMapping("/delete/id/{id}")
    public void deleteById(@PathVariable Long id){
        userService.deleteById(id);
    }
}
