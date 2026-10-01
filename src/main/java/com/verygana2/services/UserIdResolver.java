package com.verygana2.services;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.verygana2.repositories.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Traduce entre el id interno de un usuario (Long, PK autoincremental) y su publicId (UUID).
 *
 * Regla del sistema: el id interno NUNCA sale hacia el frontend (respuestas, JWT, URLs);
 * todo lo expuesto usa publicId. El backend recibe publicIds, los traduce aquí y opera
 * internamente con el id privado (joins, FKs, lógica de negocio).
 *
 * Ambos valores son inmutables una vez creado el usuario, así que el cache nunca queda
 * desactualizado; solo se cachean aciertos (un publicId inexistente no ocupa memoria).
 */
@Service
@RequiredArgsConstructor
public class UserIdResolver {

    private final UserRepository userRepository;

    private final Cache<UUID, Long> idByPublicId = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofHours(6))
            .build();

    private final Cache<Long, UUID> publicIdById = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofHours(6))
            .build();

    /** publicId -> id interno. Lanza 404 si no existe. */
    public Long toInternalId(UUID publicId) {
        Objects.requireNonNull(publicId, "publicId is required");
        Long cached = idByPublicId.getIfPresent(publicId);
        if (cached != null) return cached;

        Long id = userRepository.findIdByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("User with public id: " + publicId + " not found"));
        remember(id, publicId);
        return id;
    }

    /** Variante null-safe para filtros opcionales (ej. {@code ?userPublicId=} en búsquedas). */
    public Long toInternalIdOrNull(UUID publicId) {
        return publicId == null ? null : toInternalId(publicId);
    }

    /** id interno -> publicId. Devuelve null si el id es null o no existe (usuario borrado). */
    public UUID toPublicId(Long id) {
        if (id == null) return null;
        UUID cached = publicIdById.getIfPresent(id);
        if (cached != null) return cached;

        UUID publicId = userRepository.findPublicIdById(id).orElse(null);
        if (publicId != null) remember(id, publicId);
        return publicId;
    }

    /** Traducción en lote para listados/páginas: una sola query para los ids que no estén en cache. */
    public Map<Long, UUID> toPublicIds(Collection<Long> ids) {
        Map<Long, UUID> result = new HashMap<>();
        List<Long> missing = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> {
                    UUID cached = publicIdById.getIfPresent(id);
                    if (cached != null) result.put(id, cached);
                    return cached == null;
                })
                .toList();

        if (!missing.isEmpty()) {
            userRepository.findPublicIdsByIds(missing).forEach(pair -> {
                result.put(pair.getId(), pair.getPublicId());
                remember(pair.getId(), pair.getPublicId());
            });
        }
        return result;
    }

    private void remember(Long id, UUID publicId) {
        idByPublicId.put(publicId, id);
        publicIdById.put(id, publicId);
    }
}
