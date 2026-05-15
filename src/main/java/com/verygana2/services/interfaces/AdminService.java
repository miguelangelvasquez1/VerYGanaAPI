package com.verygana2.services.interfaces;

import org.springframework.data.domain.Pageable;


import com.verygana2.models.AdminReport;
import com.verygana2.models.enums.AdminActionType;

import org.springframework.data.domain.Page;

public interface AdminService {

    Page<AdminReport> findByUserId (Long userId, Pageable pageable);
    Page<AdminReport> findByActionType(AdminActionType actionType, Pageable pageable);
    
}
