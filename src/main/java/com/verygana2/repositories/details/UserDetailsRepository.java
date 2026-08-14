package com.verygana2.repositories.details;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.Role;
import com.verygana2.models.userDetails.UserDetails;

@Repository
public interface UserDetailsRepository extends JpaRepository<UserDetails, Long>{
    
    @Query("""
            SELECT COUNT(u) from UserDetails u WHERE
            u.user.role = :role
            AND u.user.userState = com.verygana2.models.enums.UserState.ACTIVE
            """)
    Integer countActiveUsersByRole (@Param("role") Role role);

    @Query("""
            SELECT u FROM UserDetails u
            WHERE u.user.publicId = :publicId
            """)
    Optional<UserDetails> findByPublicId (UUID publicId);
}
