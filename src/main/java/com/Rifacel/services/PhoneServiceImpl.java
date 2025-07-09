package com.Rifacel.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Rifacel.models.Phone;
import com.Rifacel.repositories.PhoneRepository;
import com.Rifacel.services.interfaces.PhoneService;

@Service
public class PhoneServiceImpl implements PhoneService{
    
    @Autowired
    private PhoneRepository phoneRepository;

    @Override
    public List<Phone> getByStateTrue() {
        return phoneRepository.findByStateTrue();
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
