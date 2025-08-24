package com.VerYGana.services;

import java.time.LocalDateTime;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.VerYGana.exceptions.EmailAlreadyExistsException;
import com.VerYGana.exceptions.PhoneNumberAlreadyExistsException;
import com.VerYGana.models.User;
import com.VerYGana.models.Wallet;
import com.VerYGana.repositories.UserRepository;
import com.VerYGana.security.auth.UserRegisterRequest;
import com.VerYGana.services.interfaces.UserService;
import com.VerYGana.services.interfaces.WalletService;


@Service

public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User registerUser(UserRegisterRequest user) {
        if (emailExists(user.getEmail())) {
            throw new EmailAlreadyExistsException(user.getEmail());
        }
        if (phoneExists(user.getPhoneNumber())) {
            throw new PhoneNumberAlreadyExistsException(user.getPhoneNumber());
        }

        User newUser = new User(user);
        walletService.createWallet(newUser);

        newUser.setPassword(passwordEncoder.encode(user.getPassword())); //Encriptacion de contraseña
        newUser.setRegisteredDate(LocalDateTime.now());
        return userRepository.save(newUser);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(String id) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("User", User.class));
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {

        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ObjectNotFoundException("User", User.class));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean phoneExists(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank() || phoneNumber.length() != 10) {
            throw new IllegalArgumentException("invalid phone number");
        }
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("invalid id");
        }
        userRepository.deleteById(id);
    }
}
