package com.verygana2.repositories;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.User;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.AccountStatus;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    Optional<User> findByEmailOrPhoneNumber(String email, String phoneNumber);
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userDetails WHERE u.accountStatus = :state")
    List<User> findByAccountStatus(@Param("state") AccountStatus state);

    @Query("SELECT u FROM User u WHERE u.accountStatus = :state AND u.role IN :roles")
    List<User> findByAccountStatusAndRoleIn(@Param("state") AccountStatus state, @Param("roles") List<Role> roles);

    @Query("""
            SELECT u FROM User u
            WHERE (:startDate IS NULL OR u.registeredDate >= :startDate)
            AND (:endDate IS NULL OR u.registeredDate < :endDate)
            AND (:search IS NULL OR :search = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY u.registeredDate DESC
            """)
    Page<User> findNewUsers(@Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("search") String search, Pageable pageable);

    // ── Traducción publicId <-> id interno (ver UserIdResolver) ─────────────
    @Query("SELECT u.id FROM User u WHERE u.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

    @Query("SELECT u.publicId FROM User u WHERE u.id = :id")
    Optional<UUID> findPublicIdById(@Param("id") Long id);

    @Query("SELECT u.id AS id, u.publicId AS publicId FROM User u WHERE u.id IN :ids")
    List<UserIdPair> findPublicIdsByIds(@Param("ids") Collection<Long> ids);

    interface UserIdPair {
        Long getId();
        UUID getPublicId();
    }
}

