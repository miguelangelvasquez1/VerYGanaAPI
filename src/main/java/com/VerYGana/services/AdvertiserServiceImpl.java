package com.VerYGana.services;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.VerYGana.models.Advertiser;
import com.VerYGana.repositories.AdvertiserRepository;
import com.VerYGana.security.auth.AdvertiserRegisterRequest;
import com.VerYGana.services.interfaces.AdvertiserService;
import com.VerYGana.services.interfaces.WalletService;

@Transactional
@Service
public class AdvertiserServiceImpl implements AdvertiserService {

    @Autowired
    AdvertiserRepository advertiserRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Advertiser registerAdvertiser(AdvertiserRegisterRequest advertiserRegisterRequest) {
        if (advertiserRegisterRequest == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        if (advertiserRegisterRequest.getEmail() == null || advertiserRegisterRequest.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (advertiserRepository.existsByEmail(advertiserRegisterRequest.getEmail())) {
            throw new IllegalStateException("Email already registered");
        }

        String encryptedPassword = passwordEncoder.encode(advertiserRegisterRequest.getPassword());
        advertiserRegisterRequest.setPassword(encryptedPassword);

        Advertiser advertiser = new Advertiser(advertiserRegisterRequest);

        Advertiser savedAdvertiser = advertiserRepository.save(advertiser);

        walletService.createWalletForAdvertiser(savedAdvertiser);

        return savedAdvertiser;
    }

    @Override
    public Advertiser getAdvertiserById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("AdvertiserId cannot be null or minor than zero");
        }
        return advertiserRepository.findById(id).orElseThrow(
                () -> new ObjectNotFoundException("Advertiser with id: " + id + " not found", Advertiser.class));
    }

    @Override
    public Advertiser getAdvertiserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        return advertiserRepository.findByEmail(email).orElseThrow(
                () -> new ObjectNotFoundException("Advertiser with email: " + email + " not found", Advertiser.class));
    }

    @Override
    public boolean emailExists(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        return advertiserRepository.existsByEmail(email);
    }

    @Override
    public boolean phoneExists(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }
        return advertiserRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public void deleteById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("AdvertiserId cannot be null or minor than zero");
        }
        advertiserRepository.deleteById(id);
    }

    

}
