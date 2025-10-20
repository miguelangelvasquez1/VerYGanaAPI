package com.verygana2.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.verygana2.models.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByName(String name);

}