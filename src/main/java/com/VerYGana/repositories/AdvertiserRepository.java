package com.VerYGana.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.VerYGana.models.Advertiser;
import com.VerYGana.models.User;

@Repository
public interface AdvertiserRepository extends JpaRepository<Advertiser, Long>{
    Optional<Advertiser> findByEmail(String email);
    Optional<Advertiser> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    Optional<User> findByEmailOrPhoneNumber(String email, String phoneNumber);
}
