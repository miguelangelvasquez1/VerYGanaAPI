package com.Rifacel.models;

import java.time.LocalDateTime;

import com.Rifacel.models.Enums.Department;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class User {
    private String id;
    private String name;
    private String lasNames;
    private String email;
    private String phoneNumber;
    private Department department;
    private Municipality municipality;
    private String address;
    private String password;
    private LocalDateTime registeredDate;
}
