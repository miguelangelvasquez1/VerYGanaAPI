package com.verygana2.repositories.commercial;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.commercial.diagnostic.DiagnosticQuestionnaire;

public interface DiagnosticQuestionnaireRepository extends JpaRepository<DiagnosticQuestionnaire, Long> {

    Optional<DiagnosticQuestionnaire> findFirstByActiveTrueOrderByVersionDesc();

    boolean existsByVersion(Integer version);
}
