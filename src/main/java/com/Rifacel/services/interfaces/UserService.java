package com.Rifacel.services.interfaces;


import com.Rifacel.models.User;

public interface UserService {
    User registerUser(User user);
    User getUserById(String id);
    User getUserByEmail(String email);
    boolean emailExists(String email);
    boolean phoneExists(String phoneNumber);
}
