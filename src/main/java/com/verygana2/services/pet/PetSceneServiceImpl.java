package com.verygana2.services.pet;

import com.verygana2.dtos.pet.PetSceneAdminResponseDTO;
import com.verygana2.dtos.pet.PetSceneObjectRequestDTO;
import com.verygana2.dtos.pet.PetSceneObjectResponseDTO;
import com.verygana2.dtos.pet.PetSceneRequestDTO;
import com.verygana2.dtos.pet.PetSceneResponseDTO;
import com.verygana2.mappers.pet.PetSceneMapper;
import com.verygana2.models.pets.PetScene;
import com.verygana2.models.pets.PetSceneObject;
import com.verygana2.repositories.pet.PetSceneRepository;
import com.verygana2.services.interfaces.pet.PetSceneService;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PetSceneServiceImpl implements PetSceneService {

    @Value("${cloudflare.r2.pets-cdn-domain:}")
    private String petsCdnDomain;

    @Value("${cloudflare.r2.pets-bucket-name:verygana-pets}")
    private String petsBucketName;

    private final PetSceneRepository sceneRepository;
    private final PetSceneMapper sceneMapper;

    public PetSceneServiceImpl(PetSceneRepository sceneRepository, PetSceneMapper sceneMapper) {
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

    private List<PetSceneObjectResponseDTO> mapObjects(PetScene scene) {
        return scene.getObjects().stream()
                .map(obj -> {
                    PetSceneObjectResponseDTO dto = sceneMapper.toObjectDTO(obj);
                    return new PetSceneObjectResponseDTO(
                            dto.id(), dto.type(), buildPublicUrl(obj.getObjectKey()),
                            dto.x(), dto.y(), dto.width(), dto.height(), dto.scaleMultiplier()
                    );
                })
                .toList();
    }

    private PetSceneObject fromRequestDTO(PetSceneObjectRequestDTO dto, PetScene scene) {
        PetSceneObject obj = new PetSceneObject();
        obj.setObjectId(dto.objectId());
        obj.setType(dto.type());
        obj.setObjectKey(dto.objectKey());
        obj.setX(dto.x());
        obj.setY(dto.y());
        obj.setWidth(dto.width());
        obj.setHeight(dto.height());
        obj.setScaleMultiplier(dto.scaleMultiplier() != null ? dto.scaleMultiplier() : 1.0);
        obj.setScene(scene);
        return obj;
    }

    @Override
    public List<PetSceneResponseDTO> getAllScenes() {
        return sceneRepository.findAllByActiveTrue().stream()
                .map(scene -> new PetSceneResponseDTO(scene.getSceneId(), mapObjects(scene)))
                .toList();
    }

    @Override
    public List<PetSceneAdminResponseDTO> getAllScenesAdmin() {
        return sceneRepository.findAll().stream()
                .map(scene -> new PetSceneAdminResponseDTO(
                        scene.getId(), scene.getSceneId(), scene.getActive(), mapObjects(scene)))
                .toList();
    }

    @Override
    public PetSceneAdminResponseDTO createScene(PetSceneRequestDTO dto) {
        PetScene scene = new PetScene();
        scene.setSceneId(dto.sceneId());
        scene.setActive(dto.active() != null ? dto.active() : true);

        if (dto.objects() != null) {
            dto.objects().stream()
                    .map(o -> fromRequestDTO(o, scene))
                    .forEach(scene.getObjects()::add);
        }

        PetScene saved = sceneRepository.save(scene);
        return new PetSceneAdminResponseDTO(
                saved.getId(), saved.getSceneId(), saved.getActive(), mapObjects(saved));
    }

    @Override
    public PetSceneAdminResponseDTO updateScene(Long id, PetSceneRequestDTO dto) {
        PetScene scene = sceneRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Scene not found: " + id));

        if (dto.sceneId() != null) scene.setSceneId(dto.sceneId());
        if (dto.active() != null) scene.setActive(dto.active());

        if (dto.objects() != null) {
            scene.getObjects().clear();
            dto.objects().stream()
                    .map(o -> fromRequestDTO(o, scene))
                    .forEach(scene.getObjects()::add);
        }

        PetScene saved = sceneRepository.save(scene);
        return new PetSceneAdminResponseDTO(
                saved.getId(), saved.getSceneId(), saved.getActive(), mapObjects(saved));
    }

    @Override
    public void deleteScene(Long id) {
        PetScene scene = sceneRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Scene not found: " + id));
        scene.setActive(false);
        sceneRepository.save(scene);
    }
}