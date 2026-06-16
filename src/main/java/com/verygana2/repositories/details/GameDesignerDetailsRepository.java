package com.verygana2.repositories.details;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.userDetails.GameDesignerDetails;

public interface GameDesignerDetailsRepository extends JpaRepository<GameDesignerDetails, Long> {

    Optional<GameDesignerDetails> findByDesignerCode(String designerCode);

    Optional<GameDesignerDetails> findByUser_Id(Long userId);

    boolean existsByDesignerCode(String designerCode);

    List<GameDesignerDetails> findByActiveTrue();
}
