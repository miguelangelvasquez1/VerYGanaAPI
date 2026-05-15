package com.verygana2.repositories.details;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.userDetails.AdminDetails;

public interface AdminDetailsRepository extends JpaRepository<AdminDetails, Long> {
    
}
