package com.verygana2.repositories.pqrs;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.pqrs.Pqrs;

@Repository
public interface PqrsRepository extends JpaRepository<Pqrs, Long> {

    Page<Pqrs> findByRequesterId(Long requesterId, Pageable pageable);

    @Query("SELECT p FROM Pqrs p WHERE p.assignedAdmin.user.id = :adminUserId " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:type IS NULL OR p.type = :type) " +
           "ORDER BY p.createdAt DESC")
    Page<Pqrs> findByAssignedAdminWithFilters(
            @Param("adminUserId") Long adminUserId,
            @Param("status") PqrsStatus status,
            @Param("type") PqrsType type,
            Pageable pageable);

    List<Pqrs> findByStatus(PqrsStatus status);

    List<Pqrs> findByStatusInAndDueDateBefore(List<PqrsStatus> statuses, ZonedDateTime dueDate);
}
