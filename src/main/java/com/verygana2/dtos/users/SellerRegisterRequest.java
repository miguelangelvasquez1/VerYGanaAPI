package com.verygana2.dtos.users;

import java.util.List;
import java.util.Locale.Category;

import lombok.Data;

@Data
public class SellerRegisterRequest {
    private String shopName;
    private String nit;
    private List<Category> categories;
    private String email;
    private String phoneNumber;
    private String password;
    private String department;
    private String municipio;
    private String principalAddress;
}
