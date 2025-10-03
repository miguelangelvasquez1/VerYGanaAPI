package com.VerYGana.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.VerYGana.models.AdminReport;
import com.VerYGana.models.enums2.AdminActionType;

@Repository
public interface AdminReportRepository extends JpaRepository<AdminReport, Long>{
    
    Page<AdminReport> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    Page<AdminReport> findByActionTypeOrderByCreatedAtDesc(AdminActionType actionType, Pageable pageable);
    
}
