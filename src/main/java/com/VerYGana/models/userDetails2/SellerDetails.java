package com.VerYGana.models.userDetails2;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper=false)
public class SellerDetails extends UserDetails {
    
    private String shopName;         // Nombre de la tienda
    private String taxId;            // Identificación tributaria
    private int totalProducts;       // Cantidad de productos publicados
    private double earnings;         // Ganancias acumuladas
    private String deliveryRegion;   // Región de entregas
}
