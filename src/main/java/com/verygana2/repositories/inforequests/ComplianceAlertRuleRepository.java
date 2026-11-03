package com.verygana2.repositories.inforequests;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.inforequests.ComplianceAlertRule;

public interface ComplianceAlertRuleRepository extends JpaRepository<ComplianceAlertRule, Long> {

    List<ComplianceAlertRule> findByActiveTrue();

    List<ComplianceAlertRule> findAllByOrderByCodeAscVersionDesc();

    Optional<ComplianceAlertRule> findFirstByCodeOrderByVersionDesc(String code);
}
