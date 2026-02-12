package com.verygana2.repositories.details;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.userDetails.UserDetails;

@Repository
public interface UserDetailsRepository extends JpaRepository<UserDetails, Long>{
    
}
