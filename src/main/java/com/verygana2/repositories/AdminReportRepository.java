package com.verygana2.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.verygana2.models.AdminReport;
import com.verygana2.models.enums.AdminActionType;

@Repository
public interface AdminReportRepository extends JpaRepository<AdminReport, Long>{
    
    Page<AdminReport> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    Page<AdminReport> findByActionTypeOrderByCreatedAtDesc(AdminActionType actionType, Pageable pageable);
    
}
