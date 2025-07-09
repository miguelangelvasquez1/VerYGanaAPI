package com.Rifacel.services.interfaces;

import java.util.List;

import com.Rifacel.models.Phone;

public interface PhoneService {
    List<Phone> getByStateTrue();
    List<Phone> getByMarkContainingIgnoreCase(String mark);
    List<Phone> getByVersionContainingIgnoreCase(String version);
}
