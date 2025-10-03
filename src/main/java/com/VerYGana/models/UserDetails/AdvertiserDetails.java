package com.VerYGana.models.userDetails;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper=false)
public class AdvertiserDetails extends UserDetails {
    
    private String companyName;
    private String nit;
    private Double adBuget;
}
