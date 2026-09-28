package com.verygana2.services.gameDesigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.verygana2.dtos.branding.DesignerBrandingDetailDTO;
import com.verygana2.mappers.GameDesignerMapper;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.utils.validators.games.GameConfigValidator;
import com.verygana2.utils.validators.games.SchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * El diseñador tiene que poder distinguir lo que escribió la marca.
 *
 * El borrador le llega sembrado con el contenido del anunciante, así que en su
 * formulario esas preguntas se ven igual que un valor por defecto del esquema. Sin
 * una copia aparte puede reescribirlas sin enterarse de que está pisando lo que la
 * marca pidió, y el anunciante lo descubre recién en la preview —cuando ya hay un
 * diseño entregado y una discusión por delante—.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DesignerSeesBrandContentTest {

    @Mock private BrandingRequestRepository brandingRequestRepository;
    @Mock private GameDesignerMapper gameDesignerMapper;
    /** Real: solo se usa para resolver la definición vigente del juego. */
    @Spy private GameConfigValidator gameConfigValidator = new GameConfigValidator(new SchemaValidator(new ObjectMapper()));

    @InjectMocks private GameDesignerServiceImpl service;

    private static final Map<String, Object> BRIEF = Map.of(
        "game", Map.of("questions", List.of(
            Map.of("id", 1, "question", "¿En qué año nació la marca?",
                   "options", List.of("1990", "2001"), "correct_answer_index", 1))));

    @Test
    @DisplayName("el detalle del diseñador trae el contenido de la marca, aparte del borrador")
    void detailCarriesTheBrief() {
        Map<String, Object> draftEditado = Map.of(
            "game", Map.of("questions", List.of(
                Map.of("id", 1, "question", "¿En qué año nació la marca?",
                       "options", List.of("1990", "2001"), "correct_answer_index", 0))));

        BrandingRequest request = BrandingRequest.builder()
            .id(5L)
            .brandName("Coca Cola")
            .game(Game.builder().id(19L).title("Trivia Quiz").url("trivia-quiz")
                .configDefinitions(new ArrayList<>(List.of(
                    GameConfigDefinition.builder().version(1L).jsonSchema(Map.of()).build())))
                .build())
            .corporateResources(new ArrayList<>())
            .briefData(BRIEF)
            .draftFormData(draftEditado)
            .build();

        when(brandingRequestRepository.findByIdAndAssignedDesigner_User_Id(5L, 9L))
            .thenReturn(Optional.of(request));
        when(gameDesignerMapper.toDesignerDetailDTO(any())).thenReturn(new DesignerBrandingDetailDTO());

        DesignerBrandingDetailDTO dto = service.getAssignedBrandingRequestDetail(5L, 9L);

        // Las dos copias viajan: el borrador, que es editable, y lo que mandó la marca.
        assertThat(dto.getBriefData()).isEqualTo(BRIEF);
        assertThat(dto.getDraftFormData()).isEqualTo(draftEditado);
        assertThat(dto.getBriefData()).isNotEqualTo(dto.getDraftFormData());
    }

    @Test
    @DisplayName("una solicitud sin contenido de marca no rompe el detalle")
    void detailWithoutBrief() {
        BrandingRequest request = BrandingRequest.builder()
            .id(6L)
            .game(Game.builder().id(1L).title("Tap Runner").url("Tap%20Runner")
                .configDefinitions(new ArrayList<>(List.of(
                    GameConfigDefinition.builder().version(1L).jsonSchema(Map.of()).build())))
                .build())
            .corporateResources(new ArrayList<>())
            .build();

        when(brandingRequestRepository.findByIdAndAssignedDesigner_User_Id(6L, 9L))
            .thenReturn(Optional.of(request));
        when(gameDesignerMapper.toDesignerDetailDTO(any())).thenReturn(new DesignerBrandingDetailDTO());

        assertThat(service.getAssignedBrandingRequestDetail(6L, 9L).getBriefData()).isNull();
    }
}
