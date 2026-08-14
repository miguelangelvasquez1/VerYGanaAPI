package com.verygana2.repositories.details;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.GameDesignerDetails;

@Repository
public interface GameDesignerDetailsRepository extends JpaRepository<GameDesignerDetails, Long> {

    Optional<GameDesignerDetails> findByDesignerCode(String designerCode);

    Optional<GameDesignerDetails> findByUser_Id(Long userId);

    boolean existsByDesignerCode(String designerCode);

    List<GameDesignerDetails> findByActiveTrue();

    @Query("""
            SELECT g FROM GameDesignerDetails g
            WHERE (:userState IS NULL OR g.user.userState = :userState)
            AND (:search IS NULL OR :search = ''
            OR LOWER(g.user.email) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(g.user.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(g.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(g.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(g.designerCode) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(g.bio) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<GameDesignerDetails> findGameDesigners (@Param("search") String search, @Param("userState") UserState userState, Pageable pageable);

    @Query("""
            SELECT g FROM GameDesignerDetails g
            WHERE g.user.publicId = :publicId
            """)
    Optional<GameDesignerDetails> findByPublicId (@Param("publicId") UUID publicId);
}
