package com.verygana2.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.CategoryRequestDTO;
import com.verygana2.dtos.CategoryResponseDTO;
import com.verygana2.mappers.CategoryMapper;
import com.verygana2.models.Category;
import com.verygana2.repositories.CategoryRepository;
import com.verygana2.services.interfaces.CategoryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository repository;
    private final CategoryMapper mapper;

    @Override
    public CategoryResponseDTO create(CategoryRequestDTO dto) {
        if (repository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("La categoría con ese nombre ya existe");
        }
        Category category = mapper.toEntity(dto);
        return mapper.toDTO(repository.save(category));
    }

    @Override
    public CategoryResponseDTO getById(Long id) {
        Category category = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
        return mapper.toDTO(category);
    }

    @Override
    public List<CategoryResponseDTO> getAll() { //Hacer dto en el repository
        return repository.findAll()
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponseDTO update(Long id, CategoryRequestDTO dto) {
        Category category = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
        
        if (repository.existsByName(dto.getName()) && !category.getName().equalsIgnoreCase(dto.getName())) {
            throw new IllegalArgumentException("Ya existe otra categoría con ese nombre");
        }
        
        category.setName(dto.getName());
        return mapper.toDTO(repository.save(category));
    }

    @Override
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Categoría no encontrada");
        }
        repository.deleteById(id);
    }
}
