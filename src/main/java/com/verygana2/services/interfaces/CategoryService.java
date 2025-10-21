package com.verygana2.services.interfaces;

import java.util.List;

import com.verygana2.dtos.CategoryRequestDTO;
import com.verygana2.dtos.CategoryResponseDTO;
import com.verygana2.dtos.generic.EntityCreatedResponse;

public interface CategoryService {

    EntityCreatedResponse create(CategoryRequestDTO dto);

    CategoryResponseDTO getById(Long id);

    List<CategoryResponseDTO> getAll();

    CategoryResponseDTO update(Long id, CategoryRequestDTO dto);

    void delete(Long id);
}
