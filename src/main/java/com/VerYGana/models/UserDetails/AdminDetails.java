package com.VerYGana.models.UserDetails;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper=false)
public class AdminDetails extends UserDetails {
    

}
