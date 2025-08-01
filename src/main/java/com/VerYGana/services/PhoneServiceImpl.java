package com.VerYGana.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.VerYGana.models.Phone;
import com.VerYGana.repositories.PhoneRepository;
import com.VerYGana.services.interfaces.PhoneService;

@Service
public class PhoneServiceImpl implements PhoneService{
    
    @Autowired
    private PhoneRepository phoneRepository;

    @Override
    public List<Phone> getByAvailabilityTrue() {
        return phoneRepository.findByAvailabilityTrue();
    }

    @Override
    public List<Phone> getByMarkContainingIgnoreCase(String mark) {
        if (mark == null || mark.isBlank()) {
            throw new IllegalArgumentException("invalid mark");
        }
        return phoneRepository.findByMarkContainingIgnoreCase(mark);
    }

    @Override
    public List<Phone> getByVersionContainingIgnoreCase(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("invalid version");
        }
        return phoneRepository.findByVersionContainingIgnoreCase(version);
    }
}
