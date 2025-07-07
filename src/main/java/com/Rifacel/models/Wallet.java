package com.Rifacel.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Wallet {
    private String userId;
    private double balance;
    private double blockedBalance;
}
