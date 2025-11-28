package com.verygana2.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        Category savedCategory = repository.save(category);
        return new EntityCreatedResponse(savedCategory.getId(),"Category created succesfully", Instant.now());
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
        log.info("Fetching categories from repository...");
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

    /**
     * Valida y obtiene las categorías por sus IDs.
     * Si el caché no las tiene todas, las consulta directamente en la base de datos.
     */
    @Override
    public List<Category> getValidatedCategories(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una categoría.");
        }

        // 1️ Intentar usar el caché
        List<Category> allCached = getAllCategories();
        Map<Long, Category> cachedMap = allCached.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        List<Category> selected = new ArrayList<>();
        List<Long> missingIds = new ArrayList<>();

        // 2️ Buscar en el caché primero
        for (Long id : categoryIds) {
            Category c = cachedMap.get(id);
            if (c != null) {
                selected.add(c);
            } else {
                missingIds.add(id);
            }
        }

        // 3️ Si faltan categorías, ir a la BD directamente
        if (!missingIds.isEmpty()) {
            log.info("Buscando en BD");
            List<Category> dbCategories = repository.findAllById(missingIds);

            if (dbCategories.size() != missingIds.size()) {
                // Encontrar cuál ID no existe realmente
                List<Long> foundIds = dbCategories.stream()
                        .map(Category::getId)
                        .toList();

                Long missing = missingIds.stream()
                        .filter(id -> !foundIds.contains(id))
                        .findFirst()
                        .orElse(null);

                throw new IllegalArgumentException(
                    "La categoría con ID " + missing + " no existe o fue eliminada."
                );
            }

            selected.addAll(dbCategories);
        }

        return selected;
    }
}