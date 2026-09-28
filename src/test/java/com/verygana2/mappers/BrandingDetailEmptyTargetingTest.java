package com.verygana2.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.branding.BrandingRequestDetailDTO;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;

/**
 * El detalle de una solicitud sin targeting no puede traer colecciones nulas.
 *
 * Un borrador solo tiene {@code TargetAudience} si pasó por el paso de configuración;
 * si se saltea, la fila no existe y estas dos listas salían en {@code null}. El
 * frontend hace {@code categories.map(...)} apenas recibe el detalle, dentro del
 * mismo {@code try} que envuelve la carga: la petición respondía 200 y la pantalla
 * mostraba «No se pudo cargar el detalle de la solicitud». Ningún borrador se podía
 * abrir.
 */
class BrandingDetailEmptyTargetingTest {

    private final BrandingMapper mapper = new BrandingMapperImpl();

    private static BrandingRequest sinTargeting() {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(2L);

        return BrandingRequest.builder()
            .id(13L)
            .brandName("Coca Cola")
            .status(BrandingRequestStatus.DRAFT)
            .game(Game.builder().id(19L).title("Trivia Quiz").url("trivia-quiz").build())
            .commercial(commercial)
            // Toda solicitud real nace con su definición: el detalle la usa para
            // calcular las recompensas estimadas.
            .gameConfigDefinition(GameConfigDefinition.builder()
                .version(1L)
                .scoreRewardFactor(1.0)
                .averageRewardPerSessionCents(15000L)
                .build())
            .corporateResources(new ArrayList<>())
            .build();
    }

    @Test
    @DisplayName("un borrador sin targeting trae las listas vacías, no nulas")
    void emptyInsteadOfNull() {
        BrandingRequestDetailDTO dto = mapper.toDetailDTO(sinTargeting());

        assertThat(dto.getCategories()).isNotNull().isEmpty();
        assertThat(dto.getTargetMunicipalities()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("los campos calculados sí llegan: el @AfterMapping estaba muerto")
    void computedFieldsRun() {
        // MapStruct saltea sin avisar los @AfterMapping cuyo target es el DTO cuando el
        // DTO se construye por builder. Con la firma anterior, el nombre del diseñador
        // asignado salía siempre en null en el detalle del anunciante.
        GameDesignerDetails designer = new GameDesignerDetails();
        designer.setName("Ana");
        designer.setLastName("Díaz");
        designer.setDesignerCode("GD-007");

        BrandingRequest request = sinTargeting();
        request.setAssignedDesigner(designer);

        BrandingRequestDetailDTO dto = mapper.toDetailDTO(request);

        assertThat(dto.getAssignedDesignerName()).isEqualTo("Ana Díaz");
        assertThat(dto.getAssignedDesignerCode()).isEqualTo("GD-007");
    }

    @Test
    @DisplayName("el resto del detalle se mapea igual")
    void keepsTheRest() {
        // Que el relleno no tape un mapeo roto: si el detalle llegara vacío, el
        // borrador abriría pero sin datos.
        BrandingRequestDetailDTO dto = mapper.toDetailDTO(sinTargeting());

        assertThat(dto.getId()).isEqualTo(13L);
        assertThat(dto.getBrandName()).isEqualTo("Coca Cola");
        assertThat(dto.getGameName()).isEqualTo("Trivia Quiz");
        assertThat(dto.getStatus()).isEqualTo(BrandingRequestStatus.DRAFT);
    }
}
