package com.VerYGana.models.userDetails2;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper=false)
public class ConsumerDetails extends UserDetails {
    
    private String name;
    private String lastName;
    private Integer adsWatched;
    private Integer totalWithdraws;
    private Integer dailyAdCount;
    private String referralCode;
    private String department;
    private String municipio;
}
