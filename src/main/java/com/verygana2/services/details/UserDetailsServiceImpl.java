package com.verygana2.services.details;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;

import com.verygana2.models.userDetails.UserDetails;
import com.verygana2.repositories.details.UserDetailsRepository;
import com.verygana2.services.interfaces.details.UserDetailsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService{

    private final UserDetailsRepository userDetailsRepository;

    @Override
    public UserDetails getUserById(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be positive");
        }

        return userDetailsRepository.findById(userId).orElseThrow(() -> new ObjectNotFoundException("User with id: " + userId + " not found " , UserDetails.class));
    }
    
}
