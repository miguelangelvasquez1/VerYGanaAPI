package com.verygana2.models.userDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "compliance_officer_details")
@Data
@EqualsAndHashCode(callSuper = false)
public class ComplianceOfficerDetails extends UserDetails {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "badge_number", nullable = false, unique = true, length = 20)
    private String badgeNumber;
}