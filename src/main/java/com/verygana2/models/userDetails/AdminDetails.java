package com.verygana2.models.userDetails;

import java.time.ZonedDateTime;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper=false)
public class AdminDetails extends UserDetails {
    private String adminCode;

    // Cursor de rotación para la asignación equitativa de PQRS: el admin con el valor
    // más antiguo (o nulo) es el siguiente en recibir un PQRS.
    private ZonedDateTime lastPqrsAssignedAt;
}
