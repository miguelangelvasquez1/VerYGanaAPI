package com.Rifacel.controllers;

import java.net.URI;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Rifacel.models.User;
import com.Rifacel.security.auth.UserRegisterRequest;
import com.Rifacel.services.interfaces.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    // Registrar un usuario
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody UserRegisterRequest user) {
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
