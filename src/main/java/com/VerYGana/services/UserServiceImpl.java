package com.VerYGana.services;


import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.VerYGana.dtos.auth.UserRegisterRequest;
import com.VerYGana.models.User;
import com.VerYGana.repositories.UserRepository;
import com.VerYGana.services.interfaces.UserService;
import com.VerYGana.services.interfaces.WalletService;


@Transactional
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User registerUser(UserRegisterRequest userRegisterRequest) {

        if (userRegisterRequest == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        if (userRegisterRequest.getEmail() == null || userRegisterRequest.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (userRepository.existsByEmail(userRegisterRequest.getEmail())) {
            throw new IllegalStateException("Email already registered");
        }

        if (userRepository.existsByPhoneNumber(userRegisterRequest.getPhoneNumber())) {
            throw new IllegalStateException("Phone number already registered");
        }

        String encryptedPassword = passwordEncoder.encode(userRegisterRequest.getPassword());
        userRegisterRequest.setPassword(encryptedPassword);

        User user = new User(userRegisterRequest);

        User savedUser = userRepository.save(user);

        walletService.createWallet(savedUser.getId());

        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("UserId cannot be null or minor than 1");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("User not found for userId: " + id, User.class));
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
            throw new IllegalArgumentException("Invalid phone number");
        }
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public void deleteById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("UserId cannot be null or minor than 1");
        }
        userRepository.deleteById(id);
    }
}

