package com.VerYGana.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.VerYGana.models.User;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    Optional<User> findByEmailOrPhone(String email, String phone);
    @Query("SELECT u FROM User u WHERE (u.email = :identifier OR u.phone = :identifier) AND u.password = :password")
    Optional<User> findByEmailOrPhoneAndPassword(@Param("identifier") String identifier, @Param("password") String password);
}

