package com.verygana2.repositories;

import com.verygana2.models.Avatar;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AvatarRepository extends JpaRepository<Avatar, Long> {
    List<Avatar> findByActiveTrueOrderBySortOrderAsc();
}