package com.Rifacel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Rifacel.models.User;

public interface UserRepository extends JpaRepository<User, String> {
    // Additional query methods can be defined here if needed
}
