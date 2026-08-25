package com.verygana2.services.pet;

import com.verygana2.dtos.pet.PetSceneAdminResponseDTO;
import com.verygana2.dtos.pet.PetSceneCanvasDTO;
import com.verygana2.dtos.pet.PetSceneObjectAdminResponseDTO;
import com.verygana2.dtos.pet.PetSceneObjectRequestDTO;
import com.verygana2.dtos.pet.PetSceneObjectResponseDTO;
import com.verygana2.dtos.pet.PetSceneRequestDTO;
import com.verygana2.dtos.pet.PetSceneResponseDTO;
import com.verygana2.mappers.pet.PetSceneMapper;
import com.verygana2.models.pets.PetScene;
import com.verygana2.models.pets.PetSceneObject;
import com.verygana2.repositories.pet.PetSceneRepository;
import com.verygana2.services.interfaces.pet.PetSceneService;
import com.verygana2.utils.validators.pet.PetSchemaValidator;
import com.verygana2.utils.validators.games.ValidationPipeline.ValidationError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ValidationException;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PetSceneServiceImpl implements PetSceneService {

    @Value("${pets.scene-canvas.width:1920}")
    private int canvasWidth;

    @Value("${pets.scene-canvas.height:1080}")
    private int canvasHeight;

    @Value("${pets.scene-canvas.y-axis:UP}")
    private String canvasYAxis;

    @Value("${pets.scene-canvas.anchor:CENTER}")
    private String canvasAnchor;

    private final PetSceneRepository sceneRepository;
    private final PetSceneMapper sceneMapper;
    private final PetSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final PetAssetUrlResolver urlResolver;

    public PetSceneServiceImpl(PetSceneRepository sceneRepository, PetSceneMapper sceneMapper,
                               PetSchemaValidator schemaValidator, ObjectMapper objectMapper,
                               PetAssetUrlResolver urlResolver) {
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
        this.sceneRepository = sceneRepository;
        this.sceneMapper = sceneMapper;
        this.urlResolver = urlResolver;
    }

    private String buildPublicUrl(String objectKey) {
        return urlResolver.resolve(objectKey);
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

    /**
     * Versión para el panel: además de la url lleva `objectId` y `objectKey`, que son
     * los campos que el formulario reenvía al guardar. Con el DTO del juego llegaban
     * como `id` y `url`, el front los leía como undefined y el diseñador se encontraba
     * el identificador y el asset en blanco cada vez que abría una escena.
     */
    private List<PetSceneObjectAdminResponseDTO> mapObjectsAdmin(PetScene scene) {
        return scene.getObjects().stream()
                .map(obj -> new PetSceneObjectAdminResponseDTO(
                        obj.getObjectId(), obj.getType(), obj.getObjectKey(),
                        buildPublicUrl(obj.getObjectKey()),
                        obj.getX(), obj.getY(), obj.getWidth(), obj.getHeight(),
                        obj.getScaleMultiplier()))
                .toList();
    }

    /**
     * Valida la escena contra el JSON Schema antes de persistirla.
     *
     * El DTO acepta todo nulo, así que sin esto se podía guardar una escena sin
     * objetos, o con objetos sin objectKey ni medidas: el juego la descarga y no
     * renderiza nada, sin ningún error que explique por qué.
     */
    private void assertValid(PetSceneRequestDTO dto) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.convertValue(dto, Map.class);

        List<ValidationError> errors =
                schemaValidator.validate(PetSchemaValidator.PetSchema.SCENE, payload);

        if (!errors.isEmpty()) {
            throw new ValidationException("La escena no es válida: " + PetSchemaValidator.describe(errors));
        }
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

    /**
     * Igual que {@link #getAllScenes()} pero sin filtrar por `active`, y con la misma
     * forma de respuesta: el build consume el mismo JSON en preview que en producción,
     * así que lo que ve el diseñador es exactamente lo que verá el consumidor.
     *
     * Es la contraparte de {@code getPreviewAssets} en juegos: allí el token de sesión
     * "preview" sirve assets de un BrandingRequest sin publicar; acá sirve escenas sin
     * publicar. Se mantiene el mismo patrón a propósito.
     */
    @Override
    public List<PetSceneResponseDTO> getScenesForPreview(Integer sceneId) {
        List<PetScene> scenes = sceneId != null
                ? sceneRepository.findAllBySceneId(sceneId)
                : sceneRepository.findAll();

        return scenes.stream()
                .map(scene -> new PetSceneResponseDTO(scene.getSceneId(), mapObjects(scene)))
                .toList();
    }

    @Override
    public List<PetSceneAdminResponseDTO> getAllScenesAdmin() {
        return sceneRepository.findAll().stream()
                .map(scene -> new PetSceneAdminResponseDTO(
                        scene.getId(), scene.getSceneId(), scene.getActive(), mapObjectsAdmin(scene)))
                .toList();
    }

    @Override
    public PetSceneAdminResponseDTO createScene(PetSceneRequestDTO dto) {
        assertValid(dto);

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
                saved.getId(), saved.getSceneId(), saved.getActive(), mapObjectsAdmin(saved));
    }

    @Override
    public PetSceneAdminResponseDTO updateScene(Long id, PetSceneRequestDTO dto) {
        assertValid(dto);

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
                saved.getId(), saved.getSceneId(), saved.getActive(), mapObjectsAdmin(saved));
    }

    /**
     * Sistema de coordenadas que usa el lienzo del editor para situar los objetos.
     *
     * Los defectos están verificados contra el juego, no asumidos:
     *
     *  - 1920x1080 sale del build. Los CanvasScaler de la escena usan
     *    ScaleWithScreenSize con referencia (1080,1920) y match 0.5; la media
     *    geométrica de eso en un marco 16:9 —el que fuerza `fitAspectRatio()` en
     *    el index.html— da factor 1, así que el espacio lógico es 1920x1080 pese
     *    a que la referencia esté en vertical.
     *  - Y hacia arriba y x/y en el centro del objeto se comprobaron comparando
     *    el lienzo del editor con la previsualización real (2026-08-17). De las
     *    cuatro combinaciones posibles era la única que cuadraba.
     *
     * Siguen siendo configurables por si el juego cambia el montaje de la escena.
     */
    @Override
    public PetSceneCanvasDTO getSceneCanvas() {
        return new PetSceneCanvasDTO(canvasWidth, canvasHeight, canvasYAxis, canvasAnchor);
    }

    @Override
    public void deleteScene(Long id) {
        PetScene scene = sceneRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Scene not found: " + id));
        scene.setActive(false);
        sceneRepository.save(scene);
    }
}