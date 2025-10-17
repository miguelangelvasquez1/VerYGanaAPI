package com.verygana2.dtos.users;

import java.util.List;
import lombok.Data;

@Data
public class ConsumerRegisterRequest {
    private String name;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String password;
    private String department;
    private String municipio;
    private String address;
    private List<String> interests;
}
