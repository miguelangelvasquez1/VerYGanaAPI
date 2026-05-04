package com.verygana2.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.models.AdminReport;
import com.verygana2.models.enums.AdminActionType;
import com.verygana2.services.interfaces.AdminService;

@Service
public class AdminServiceImpl implements AdminService{

    @Override
    public Page<AdminReport> findByUserId(Long userId, Pageable pageable) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findByUserId'");
    }

    @Override
    public Page<AdminReport> findByActionType(AdminActionType actionType, Pageable pageable) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findByActionType'");
    }
    
}
