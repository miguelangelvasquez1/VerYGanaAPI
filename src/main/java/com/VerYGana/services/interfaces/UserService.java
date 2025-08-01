package com.VerYGana.services.interfaces;


import com.VerYGana.models.User;
import com.VerYGana.security.auth.UserRegisterRequest;

public interface UserService {
    User registerUser(UserRegisterRequest user);
    User getUserById(String id);
    User getUserByEmail(String email);
    boolean emailExists(String email);
    boolean phoneExists(String phoneNumber);
    void deleteById(String id);
}
