package com.Rifacel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Rifacel.models.Phone;

public interface PhoneRepository extends JpaRepository<Phone, String> {
    // Additional query methods can be defined here if needed
}
