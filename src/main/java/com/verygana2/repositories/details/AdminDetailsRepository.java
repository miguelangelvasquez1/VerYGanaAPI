package com.verygana2.repositories.details;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.AdminDetails;

import jakarta.persistence.LockModeType;

@Repository
public interface AdminDetailsRepository extends JpaRepository<AdminDetails, Long> {

       // Candidatos activos para la rotación de PQRS, ordenados por el que lleva más
       // tiempo
       // esperando su turno (lastPqrsAssignedAt más antiguo o nulo primero). El lock
       // pesimista
       // evita que dos PQRS concurrentes se asignen al mismo admin.
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT a FROM AdminDetails a JOIN a.user u " +
                     "WHERE u.role = com.verygana2.models.enums.Role.ADMIN " +
                     "AND u.userState = com.verygana2.models.enums.UserState.ACTIVE " +
                     "ORDER BY a.lastPqrsAssignedAt ASC, a.id ASC")
       List<AdminDetails> findActiveAdminsForPqrsAssignmentForUpdate(Pageable pageable);

       // Todos los admins activos, para difundir notificaciones (sin lock: no hay
       // asignación de turno de por medio).
       @Query("SELECT a FROM AdminDetails a JOIN a.user u " +
                     "WHERE u.role = com.verygana2.models.enums.Role.ADMIN " +
                     "AND u.userState = com.verygana2.models.enums.UserState.ACTIVE")
       List<AdminDetails> findActiveAdmins();

       @Query("""
                     SELECT a FROM AdminDetails a
                     WHERE (:userState IS NULL OR a.user.userState = :userState)
                     AND (:search IS NULL OR :search = ''
                     OR LOWER(a.user.email) LIKE LOWER(CONCAT('%', :search, '%'))
                     OR LOWER(a.user.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                     OR LOWER(a.adminCode) LIKE LOWER(CONCAT('%', :search, '%')))
                         """)
       Page<AdminDetails> findAdmins(@Param("search") String search, @Param("userState") UserState userState,
                     Pageable pageable);

       @Query("""
                     SELECT a FROM AdminDetails a
                     WHERE a.user.publicId = :publicId
                            """)
       Optional<AdminDetails> findByPublicId(@Param("publicId") UUID publicId);

}
