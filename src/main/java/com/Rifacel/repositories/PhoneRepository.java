package com.Rifacel.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Rifacel.models.Phone;

public interface PhoneRepository extends JpaRepository<Phone, String> {
    List<Phone> findByStateTrue();
    List<Phone> findByMarckContainingIfnoreCase(String mark);
    List<Phone> findByVersionContainingIgnoreCase(String version);
}
