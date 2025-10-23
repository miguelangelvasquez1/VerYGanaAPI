package com.verygana2.repositories;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.verygana2.models.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByName(String name);
    
    long countByIdIn(List<Long> ids);

    @Cacheable("categories")
    @Query("SELECT c FROM Category c")
    List<Category> findAllCached();
}