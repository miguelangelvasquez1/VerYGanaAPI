package com.verygana2.repositories.details;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.Gender;
import com.verygana2.models.enums.UserLevel;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.ConsumerDetails;

@Repository
public interface ConsumerDetailsRepository extends JpaRepository<ConsumerDetails, Long> {
    @Query("""
            SELECT c FROM ConsumerDetails c
            JOIN FETCH c.user u
            WHERE c.id = :consumerId
            """)
    Optional<ConsumerDetails> findConsumerProfileById(@Param("consumerId") Long consumerId);

    Optional<ConsumerDetails> findByReferralCode(String referralCode);
    Optional<ConsumerDetails> findByUserId(Long userId);
    boolean existsByReferralCode(String referralCode);

    Optional<ConsumerDetails> findByUserEmail(String email);
    int countByReferredBy(ConsumerDetails referredBy);

    @Query("""
        SELECT c FROM ConsumerDetails c
        JOIN FETCH c.user u
        WHERE c.referredBy = :referrer
        ORDER BY u.registeredDate DESC
        """)
    List<ConsumerDetails> findReferralsByReferrer(@Param("referrer") ConsumerDetails referrer);

    @Query("""
            SELECT c FROM ConsumerDetails c
            LEFT JOIN UserLevelProfile ulp ON ulp.consumer = c
            WHERE (:level IS NULL OR ulp.currentLevel = :level)
            AND (:userState IS NULL OR c.user.userState = :userState)
            AND (:maxAge IS NULL OR c.age <= :maxAge)
            AND (:minAge IS NULL OR c.age >= :minAge)
            AND (:gender IS NULL OR c.gender = :gender)
            AND (:departmentCode IS NULL OR :departmentCode = '' OR c.municipality.departmentCode = :departmentCode)
            AND (:municipalityCode IS NULL OR :municipalityCode = '' OR c.municipality.code = :municipalityCode)
            AND (:startDate IS NULL OR c.user.registeredDate >= :startDate)
            AND (:endDate IS NULL OR c.user.registeredDate < :endDate)
            AND (:search IS NULL OR :search = ''
            OR LOWER(c.user.email) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.user.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.userName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.documentNumber) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.referralCode) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.departmentName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(c.municipalityName) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<ConsumerDetails> getConsumers(@Param("level") UserLevel level, @Param("search") String search, @Param("userState") UserState userState, @Param("maxAge") Integer maxAge,
    @Param("minAge") Integer minAge, @Param("gender") Gender gender, @Param("departmentCode") String departmentCode, @Param("municipalityCode") String municipalityCode, @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, Pageable pageable);

    @Query("""
                     SELECT c FROM ConsumerDetails c
                     WHERE c.user.publicId = :publicId
                            """)
    Optional<ConsumerDetails> findByPublicId (UUID publicId);
}
