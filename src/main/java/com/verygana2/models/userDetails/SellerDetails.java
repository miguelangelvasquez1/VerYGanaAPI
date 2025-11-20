package com.verygana2.models.userDetails;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper=false)
public class SellerDetails extends UserDetails {
    private String shopName;  
    private String nit; 
}
