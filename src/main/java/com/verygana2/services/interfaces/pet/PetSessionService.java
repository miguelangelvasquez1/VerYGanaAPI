package com.verygana2.services.interfaces.pet;

import com.verygana2.dtos.pet.PetSessionResponseDTO;

public interface PetSessionService {
    PetSessionResponseDTO initSession(Long consumerId);
    void validateSession(String sessionToken, String userHash);
}