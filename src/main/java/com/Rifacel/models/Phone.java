package com.Rifacel.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Phone {
    private String id;
    private String mark;
    private String version;
    private double price;
    private String image;
    private String infoURL;
    private boolean state;
}
