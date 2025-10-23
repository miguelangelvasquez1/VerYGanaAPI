package com.verygana2.services;

import java.time.Instant;
import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.CategoryRequestDTO;
import com.verygana2.dtos.CategoryResponseDTO;
import com.verygana2.dtos.generic.EntityCreatedResponse;
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
    @CacheEvict(value = "categories", allEntries = true)
    public EntityCreatedResponse create(CategoryRequestDTO dto) {
        if (repository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Already exists a category with that name");
        }
        Category category = mapper.toEntity(dto);
        repository.save(category);
        return new EntityCreatedResponse("Category created succesfully", Instant.now());
    }

    @Override
    public CategoryResponseDTO getById(Long id) {
        Category category = repository.findById(id)
            .orElseThrow(() -> new ObjectNotFoundException("Category with id: " + id + " not found", Category.class));
        return mapper.toDTO(category);
    }

    @Override
    @Cacheable("categories")
    public List<Category> getAllCategories() {
        return repository.findAll();
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponseDTO update(Long id, CategoryRequestDTO dto) {
        Category category = repository.findById(id)
            .orElseThrow(() -> new ObjectNotFoundException("Category with id: " + id + " not found", Category.class));
        
        if (repository.existsByName(dto.getName()) && !category.getName().equalsIgnoreCase(dto.getName())) {
            throw new IllegalArgumentException("Already exists a category with that name");
        }
        
        category.setName(dto.getName());
        return mapper.toDTO(repository.save(category));
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ObjectNotFoundException("Category with id: " + id + " not found", Category.class);
        }
        repository.deleteById(id);
    }
}
