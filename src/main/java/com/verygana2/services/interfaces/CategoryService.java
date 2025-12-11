package com.verygana2.services.interfaces;

import java.util.List;

import com.verygana2.dtos.CategoryRequestDTO;
import com.verygana2.dtos.CategoryResponseDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.models.Category;

public interface CategoryService {

    EntityCreatedResponseDTO create(CategoryRequestDTO dto);

    CategoryResponseDTO getById(Long id);

    List<Category> getAllCategories();

    CategoryResponseDTO update(Long id, CategoryRequestDTO dto);

    void delete(Long id);

    List<Category> getValidatedCategories(List<Long> categoryIds);
}
