package com.VerYGana.services.interfaces;

import com.VerYGana.models.Advertiser;
import com.VerYGana.security.auth.AdvertiserRegisterRequest;

public interface AdvertiserService {
    Advertiser registerAdvertiser (AdvertiserRegisterRequest advertiserRegisterRequest);
    Advertiser getAdvertiserById(Long id);
    Advertiser getAdvertiserByEmail(String email);
    boolean emailExists(String email);
    boolean phoneExists(String phoneNumber);
    void deleteById(Long id);
}
