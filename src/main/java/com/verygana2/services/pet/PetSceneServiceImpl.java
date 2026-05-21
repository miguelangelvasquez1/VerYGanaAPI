package com.verygana2.services.pet;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.pet.PetSceneObjectResponseDTO;
import com.verygana2.dtos.pet.PetSceneResponseDTO;

import com.verygana2.mappers.pet.PetSceneMapper;

import com.verygana2.repositories.pet.PetSceneRepository;
import com.verygana2.services.interfaces.pet.PetSceneService;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

@Service
public class PetSceneServiceImpl implements PetSceneService {

    @Value("${cloudflare.r2.pets-cdn-domain:}")
    private String petsCdnDomain;

    @Value("${cloudflare.r2.pets-bucket-name:verygana-pets}")
    private String petsBucketName;

    private final PetSceneRepository sceneRepository;
    private final PetSceneMapper sceneMapper;

    public PetSceneServiceImpl(
            PetSceneRepository sceneRepository,
            PetSceneMapper sceneMapper,
            ObjectMapper objectMapper
    ) {
        this.sceneRepository = sceneRepository;
        this.sceneMapper = sceneMapper;
    }

    private String buildPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return "";
        if (petsCdnDomain != null && !petsCdnDomain.isBlank()) {
            return String.format("https://%s/%s", petsCdnDomain, objectKey);
        }
        return String.format("https://%s.r2.dev/%s", petsBucketName, objectKey);
    }

    @Override
    public List<PetSceneResponseDTO> getAllScenes() {
        return sceneRepository.findAllByActiveTrue()
                .stream()
                .map(scene -> {
                    List<PetSceneObjectResponseDTO> objects = scene.getObjects()
                            .stream()
                            .map(obj -> {
                                PetSceneObjectResponseDTO dto = sceneMapper.toObjectDTO(obj);
                                String url = buildPublicUrl(obj.getObjectKey());
                                return new PetSceneObjectResponseDTO(
                                        dto.id(), dto.type(), url,
                                        dto.x(), dto.y(), dto.width(),
                                        dto.height(), dto.scaleMultiplier()
                                );
                            })
                            .toList();
                    return new PetSceneResponseDTO(scene.getSceneId(), objects);
                })
                .toList();
    }
}