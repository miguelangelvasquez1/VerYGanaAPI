package com.verygana2.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.verygana2.dtos.impactStory.CreateImpactStoryRequestDTO;
import com.verygana2.dtos.impactStory.ImpactStoryResponseDTO;
import com.verygana2.dtos.impactStory.StoryMediaResponseDTO;
import com.verygana2.dtos.impactStory.UpdateImpactStoryRequestDTO;
import com.verygana2.models.ImpactStory.ImpactStory;
import com.verygana2.models.ImpactStory.StoryMediaAsset;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ImpactStoryMapper {

    // ── CreateImpactStoryRequestDTO → ImpactStory ─────────────────────────────

    /**
     * Crea un nuevo entity desde el request.
     * Los mediaFiles NO se mapean aquí: el servicio los construye manualmente
     * a partir de los StoryMediaAsset validados.
     */
    @Mapping(target = "id",         ignore = true)
    @Mapping(target = "mediaFiles", ignore = true)
    @Mapping(target = "createdAt",  ignore = true)
    @Mapping(target = "updatedAt",  ignore = true)
    ImpactStory toEntity(CreateImpactStoryRequestDTO request);

    // ── UpdateImpactStoryRequestDTO → ImpactStory (patch) ────────────────────

    /**
     * Aplica los campos no-null del request al entity existente.
     * Campos ignorados no se tocan gracias a IGNORE nullValuePropertyMappingStrategy.
     */
    @Mapping(target = "id",         ignore = true)
    @Mapping(target = "mediaFiles", ignore = true)
    @Mapping(target = "createdAt",  ignore = true)
    @Mapping(target = "updatedAt",  ignore = true)
    void updateEntity(@MappingTarget ImpactStory story, UpdateImpactStoryRequestDTO request);

    // ── ImpactStory → ImpactStoryResponseDTO ─────────────────────────────────

    // @Mapping(target = "mediaFiles", source = "mediaFiles")
    ImpactStoryResponseDTO toResponse(ImpactStory story);

    // ── StoryMedia → StoryMediaResponseDTO ───────────────────────────────────

    StoryMediaResponseDTO toMediaResponse(StoryMediaAsset media);

    List<StoryMediaResponseDTO> toMediaResponseList(List<StoryMediaAsset> mediaList);
}
