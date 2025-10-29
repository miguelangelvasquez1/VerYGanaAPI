package com.verygana2.services;

import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.enums.PlatformTransactionType;
import com.verygana2.models.treasury.PlatformTransaction;
import com.verygana2.repositories.PlatformTransactionRepository;
import com.verygana2.services.interfaces.PlatformTransactionService;


import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PlatformTransactionServiceImpl implements PlatformTransactionService{

    private final PlatformTransactionRepository platformTransactionRepository;

    @Override
    @Transactional(readOnly = true)
    public PlatformTransaction getByReferenceId(String referenceId) {
        if (referenceId.isBlank()) {
            throw new IllegalArgumentException("PlatformTransaction reference id cannot be empty");
        }
        return platformTransactionRepository.findByReferenceId(referenceId).orElseThrow(() -> new ObjectNotFoundException("PlatformTransaction with reference id: " + referenceId + " not found", PlatformTransaction.class));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformTransaction> getByType(PlatformTransactionType type) {
        if (type == null) {
            throw new IllegalArgumentException("PlatformType cannot be null");
        }
        return platformTransactionRepository.findByType(type);
    }
    
}
