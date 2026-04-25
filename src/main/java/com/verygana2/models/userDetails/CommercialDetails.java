package com.verygana2.models.userDetails;

import java.util.List;

import com.verygana2.models.plans.Investment;
import com.verygana2.models.plans.Investment.InvestmentStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper=false)
public class CommercialDetails extends UserDetails {
    
    private String companyName;
    private String nit;

    @OneToMany(mappedBy = "commercial")
    private List<Investment> investments; // Inversiones hechas por el comercial

    public Investment getActiveInvestment() {
        if (investments == null) return null;
        return investments.stream()
            .filter(investment -> investment.getStatus().equals(InvestmentStatus.ACTIVE))
            .findFirst()
            .orElse(null);
    }
}
