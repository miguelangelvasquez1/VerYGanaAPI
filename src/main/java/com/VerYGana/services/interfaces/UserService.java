package com.VerYGana.services.interfaces;


import com.VerYGana.dtos2.auth.UserRegisterRequest;
import com.VerYGana.models.User;

public interface UserService {
    User registerUser(UserRegisterRequest userRegisterRequest);
    User getUserById(Long id);
    User getUserByEmail(String email);
    boolean emailExists(String email);
    boolean phoneExists(String phoneNumber);
    void deleteById(Long id);
}
