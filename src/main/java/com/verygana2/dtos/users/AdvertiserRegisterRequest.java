package com.verygana2.dtos.users;

import java.util.List;
import java.util.Locale.Category;

import lombok.Data;

@Data
public class AdvertiserRegisterRequest {
    private String name;
    private List<Category> categories;
    private String email;
    private String phoneNumber;
    private String password;
}
