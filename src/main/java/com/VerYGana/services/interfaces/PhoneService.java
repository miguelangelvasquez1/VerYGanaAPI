package com.VerYGana.services.interfaces;

import java.util.List;

import com.VerYGana.models.Phone;

public interface PhoneService {
    List<Phone> getByAvailabilityTrue();
    List<Phone> getByMarkContainingIgnoreCase(String mark);
    List<Phone> getByVersionContainingIgnoreCase(String version);
}
