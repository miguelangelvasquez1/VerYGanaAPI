package com.verygana2.services;

import org.hibernate.ObjectNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.user.AdvertiserRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.dtos.user.SellerRegisterDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.User;
import com.verygana2.models.userDetails.AdvertiserDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.models.userDetails.SellerDetails;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.UserService;
import com.verygana2.services.interfaces.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Transactional
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public User registerSeller(SellerRegisterDTO dto) {
        User user = userMapper.toUser(dto);
        user.setPassword(passwordEncoder.encode(dto.password()));

        SellerDetails details = userMapper.toSellerDetails(dto);
        details.setUser(user);
        user.setUserDetails(details);

        User savedUser = userRepository.save(user);
        walletService.createWallet(savedUser);

        return savedUser;
    }

    public User registerAdvertiser(AdvertiserRegisterDTO dto) {
        User user = userMapper.toUser(dto);
        user.setPassword(passwordEncoder.encode(dto.password()));

        AdvertiserDetails details = userMapper.toAdvertiserDetails(dto);
        details.setUser(user);
        user.setUserDetails(details);

        User savedUser = userRepository.save(user);
        walletService.createWallet(savedUser);

        return savedUser;
    }

    @Override
    public User registerConsumer(ConsumerRegisterDTO dto) {
        validateEmailAndPhoneNumber(dto.email(), dto.phoneNumber());

        User user = userMapper.toUser(dto);
        user.setPassword(passwordEncoder.encode(dto.password()));

        ConsumerDetails details = userMapper.toConsumerDetails(dto);
        details.setUser(user);
        user.setUserDetails(details);

        User savedUser = userRepository.save(user);
        walletService.createWallet(savedUser);

        return savedUser;
    }

    private void validateEmailAndPhoneNumber(String email, String phoneNumber) {

        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Email already registered");
        }

        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new IllegalStateException("Phone number already registered");
        }
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
