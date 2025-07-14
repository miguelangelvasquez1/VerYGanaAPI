package com.Rifacel.services.interfaces;


import com.Rifacel.models.User;
import com.Rifacel.security.auth.UserRegisterRequest;

public interface UserService {
    User registerUser(UserRegisterRequest user);
    User getUserById(String id);
    User getUserByEmail(String email);
    boolean emailExists(String email);
    boolean phoneExists(String phoneNumber);
}
