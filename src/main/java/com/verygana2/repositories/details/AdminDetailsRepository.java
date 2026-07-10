package com.verygana2.repositories.details;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.verygana2.models.userDetails.AdminDetails;

import jakarta.persistence.LockModeType;

public interface AdminDetailsRepository extends JpaRepository<AdminDetails, Long> {

    // Candidatos activos para la rotación de PQRS, ordenados por el que lleva más tiempo
    // esperando su turno (lastPqrsAssignedAt más antiguo o nulo primero). El lock pesimista
    // evita que dos PQRS concurrentes se asignen al mismo admin.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AdminDetails a JOIN a.user u " +
           "WHERE u.role = com.verygana2.models.enums.Role.ADMIN " +
           "AND u.userState = com.verygana2.models.enums.UserState.ACTIVE " +
           "ORDER BY a.lastPqrsAssignedAt ASC, a.id ASC")
    List<AdminDetails> findActiveAdminsForPqrsAssignmentForUpdate(Pageable pageable);
}
