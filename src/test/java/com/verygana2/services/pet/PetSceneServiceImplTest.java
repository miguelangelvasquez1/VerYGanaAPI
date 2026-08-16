package com.verygana2.services.pet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.pet.PetSceneObjectResponseDTO;
import com.verygana2.dtos.pet.PetSceneResponseDTO;
import com.verygana2.mappers.pet.PetSceneMapper;
import com.verygana2.models.pets.PetScene;
import com.verygana2.models.pets.PetSceneObject;
import com.verygana2.repositories.pet.PetSceneRepository;
import com.verygana2.utils.validators.pet.PetSchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PetSceneServiceImplTest {

    @Mock private PetSceneRepository sceneRepository;
    @Mock private PetSceneMapper sceneMapper;
    @Mock private PetSchemaValidator schemaValidator;

    private PetSceneServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PetSceneServiceImpl(sceneRepository, sceneMapper, schemaValidator, new ObjectMapper());
        ReflectionTestUtils.setField(service, "petsCdnDomain", "cdn.pets.test");
        ReflectionTestUtils.setField(service, "petsBucketName", "verygana-pets");
    }

    /** Escena con un único objeto, suficiente para comprobar el mapeo de URL. */
    private PetScene scene(Integer sceneId, boolean active, String objectKey) {
        PetScene scene = new PetScene();
        scene.setSceneId(sceneId);
        scene.setActive(active);

        PetSceneObject obj = new PetSceneObject();
        obj.setObjectId("obj-1");
        obj.setType("image");
        obj.setObjectKey(objectKey);
        obj.setX(100);
        obj.setY(200);
        obj.setWidth(300);
        obj.setHeight(400);
        obj.setScaleMultiplier(1.5);
        obj.setScene(scene);
        scene.getObjects().add(obj);

        return scene;
    }

    private void stubMapper() {
        when(sceneMapper.toObjectDTO(any(PetSceneObject.class)))
                .thenAnswer(inv -> {
                    PetSceneObject o = inv.getArgument(0);
                    return new PetSceneObjectResponseDTO(
                            o.getObjectId(), o.getType(), null,
                            o.getX(), o.getY(), o.getWidth(), o.getHeight(), o.getScaleMultiplier());
                });
    }

    @Nested
    @DisplayName("getAllScenes (el juego en producción)")
    class Published {

        @Test
        @DisplayName("solo pide las escenas activas: un borrador no puede colarse al consumidor")
        void soloActivas() {
            when(sceneRepository.findAllByActiveTrue()).thenReturn(List.of());

            service.getAllScenes();

            verify(sceneRepository).findAllByActiveTrue();
            verify(sceneRepository, never()).findAll();
        }
    }

    @Nested
    @DisplayName("getAllScenesAdmin (el panel del diseñador)")
    class Admin {

        @Test
        @DisplayName("lleva objectId y objectKey, no sólo la url")
        void llevaLosCamposEditables() {
            // La regresión: el panel reusaba el DTO del juego, que expone `id` y `url`.
            // El formulario leía `objectId`/`objectKey` como undefined y el diseñador
            // abría cada escena con el identificador y el asset en blanco.
            when(sceneRepository.findAll()).thenReturn(List.of(scene(3, true, "scene-objects/6/cama.png")));

            var obj = service.getAllScenesAdmin().get(0).objects().get(0);

            assertThat(obj.objectId()).isEqualTo("obj-1");
            assertThat(obj.objectKey()).isEqualTo("scene-objects/6/cama.png");
            assertThat(obj.url()).isEqualTo("https://cdn.pets.test/scene-objects/6/cama.png");
        }

        @Test
        @DisplayName("incluye las inactivas: el panel las administra, el juego no las ve")
        void incluyeInactivas() {
            when(sceneRepository.findAll())
                    .thenReturn(List.of(scene(1, true, "a.png"), scene(2, false, "b.png")));

            assertThat(service.getAllScenesAdmin())
                    .extracting(dto -> dto.active()).containsExactly(true, false);
        }
    }

    @Nested
    @DisplayName("getScenesForPreview (el diseñador revisando un borrador)")
    class Preview {

        @Test
        @DisplayName("sin sceneId devuelve también las inactivas")
        void incluyeBorradores() {
            stubMapper();
            when(sceneRepository.findAll())
                    .thenReturn(List.of(scene(1, true, "a.png"), scene(2, false, "b.png")));

            List<PetSceneResponseDTO> scenes = service.getScenesForPreview(null);

            assertThat(scenes).extracting(PetSceneResponseDTO::sceneId).containsExactly(1, 2);
            verify(sceneRepository, never()).findAllByActiveTrue();
        }

        @Test
        @DisplayName("con sceneId se limita a esa escena, aunque esté sin publicar")
        void filtraPorSceneId() {
            stubMapper();
            when(sceneRepository.findAllBySceneId(7)).thenReturn(List.of(scene(7, false, "draft.png")));

            List<PetSceneResponseDTO> scenes = service.getScenesForPreview(7);

            assertThat(scenes).singleElement()
                    .extracting(PetSceneResponseDTO::sceneId).isEqualTo(7);
            verify(sceneRepository, never()).findAll();
        }

        @Test
        @DisplayName("devuelve la misma forma que producción: el diseñador ve lo que verá el consumidor")
        void mismaFormaQueProduccion() {
            stubMapper();
            when(sceneRepository.findAllBySceneId(3)).thenReturn(List.of(scene(3, false, "sprites/silla.png")));

            PetSceneObjectResponseDTO obj = service.getScenesForPreview(3).get(0).objects().get(0);

            // La URL pública se arma igual que en el camino publicado: si el preview
            // mostrara la objectKey cruda, el juego no cargaría el asset y el diseñador
            // creería que su archivo está mal subido.
            assertThat(obj.url()).isEqualTo("https://cdn.pets.test/sprites/silla.png");
            assertThat(obj.x()).isEqualTo(100);
            assertThat(obj.y()).isEqualTo(200);
            assertThat(obj.width()).isEqualTo(300);
            assertThat(obj.height()).isEqualTo(400);
            assertThat(obj.scaleMultiplier()).isEqualTo(1.5);
        }

        @Test
        @DisplayName("un objeto sin asset subido no revienta el preview")
        void objetoSinObjectKey() {
            stubMapper();
            when(sceneRepository.findAllBySceneId(4)).thenReturn(List.of(scene(4, false, null)));

            // Caso real: el diseñador coloca el objeto antes de subirle la imagen. El
            // preview tiene que mostrarle la escena igual, no fallar con un 500.
            assertThat(service.getScenesForPreview(4).get(0).objects().get(0).url()).isEmpty();
        }
    }
}
