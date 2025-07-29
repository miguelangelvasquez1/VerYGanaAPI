package com.VerYGana.models;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Phone {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String mark;
    private String version;
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    private String image;
    private String infoURL;
    private boolean availability;
}
