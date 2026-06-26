package com.verygana2.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.User;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    Optional<User> findByEmailOrPhoneNumber(String email, String phoneNumber);
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userDetails WHERE u.userState = :state")
    List<User> findByUserState(@Param("state") UserState state);

    @Query("SELECT u FROM User u WHERE u.userState = :state AND u.role IN :roles")
    List<User> findByUserStateAndRoleIn(@Param("state") UserState state, @Param("roles") List<Role> roles);
}

