package com.Rifacel.services;

import java.time.LocalDateTime;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Rifacel.models.User;
import com.Rifacel.repositories.UserRepository;
import com.Rifacel.security.auth.UserRegisterRequest;
import com.Rifacel.services.interfaces.UserService;

@Service

public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public User registerUser(UserRegisterRequest user) {
        if (emailExists(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (phoneExists(user.getPhoneNumber())) {
            throw new IllegalArgumentException("Phone number already exists");
        }

        User newUser = new User(user);

        newUser.setRegisteredDate(LocalDateTime.now());
        // encriptar contraseña en el futuro antes de guardar el usuario en la base de
        // datos
        return userRepository.save(newUser);
    }

    @Override
    public User getUserById(String id) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("User", User.class));
    }

    @Override
    public User getUserByEmail(String email) {

        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ObjectNotFoundException("User", User.class));
    }

    @Override
    public boolean emailExists(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean phoneExists(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank() || phoneNumber.length() != 10) {
            throw new IllegalArgumentException("invalid phone number");
        }
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

}
