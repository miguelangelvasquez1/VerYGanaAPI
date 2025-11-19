package com.verygana2.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Data
@Table(name = "municipality",
       uniqueConstraints = @UniqueConstraint(columnNames = {"department_id","name"}))
public class Municipality {

    @Id
    @Column(length = 10, nullable = false)
    private String code; // Código DANE completo (ej: "63001" para Armenia)
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_code", nullable = false)
    private Department department;
    
    @Column(name = "department_code", insertable = false, updatable = false)
    private String departmentCode;
}