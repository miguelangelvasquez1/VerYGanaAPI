package com.verygana2.services.interfaces;


import com.verygana2.dtos.auth.UserRegisterRequest;
import com.verygana2.models.User;

public interface UserService {
    User registerUser(UserRegisterRequest userRegisterRequest);
    User getUserById(Long id);
    User getUserByEmail(String email);
    boolean emailExists(String email);
    boolean phoneExists(String phoneNumber);
    void deleteById(Long id);
}
