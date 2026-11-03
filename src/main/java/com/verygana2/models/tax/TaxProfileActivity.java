package com.verygana2.models.tax;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Actividad económica declarada (CIIU) en una versión de la ficha (inciso b). */
@Entity
@Immutable
@Table(name = "tax_profile_activities")
@Getter
@Setter
public class TaxProfileActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_profile_version_id", nullable = false)
    private TaxProfileVersion version;

    @Column(name = "ciiu_code", nullable = false, length = 4)
    private String ciiuCode;

    @Column(length = 300)
    private String description;

    @Column(name = "is_main", nullable = false)
    private boolean main;
}
