package com.verygana2.mappers.pqrs;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.pqrs.responses.PqrsAdminDetailDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.models.User;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.utils.pqrs.RequesterNameResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests del mapper MapStruct de PQRS ({@link PqrsMapperImpl}, generado en
 * target/generated-sources a partir de {@link PqrsMapper}). Se instancia la
 * clase generada directamente (sin contexto de Spring) para verificar que
 * el mapeo automático de campos y el post-procesamiento manual
 * ({@code @AfterMapping} que resuelve el nombre del solicitante) son correctos.
 * El test vive en el mismo paquete para poder inyectar el mock en el campo
 * {@code protected} heredado sin necesidad de un setter.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PqrsMapper")
class PqrsMapperTest {

    @Mock private RequesterNameResolver requesterNameResolver;

    private PqrsMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new PqrsMapperImpl();
        mapper.requesterNameResolver = requesterNameResolver;
    }

    private Pqrs samplePqrs() {
        User requester = new User();
        requester.setId(1L);
        requester.setEmail("consumidor@test.com");
        requester.setPhoneNumber("3001234567");

        return Pqrs.builder()
                .id(10L)
                .type(PqrsType.QUEJA)
                .status(PqrsStatus.RECIBIDA)
                .requester(requester)
                .subject("Producto no llegó")
                .description("El producto nunca llegó a mi dirección")
                .response(null)
                .dueDate(ZonedDateTime.now().plusDays(15))
                .createdAt(ZonedDateTime.now())
                .build();
    }

    @Test
    @DisplayName("toResponseDTO: copia todos los campos planos, incluyendo el radicado derivado (getBased)")
    void toResponseDTO_mapsAllFlatFields() {
        Pqrs pqrs = samplePqrs();

        PqrsResponseDTO dto = mapper.toResponseDTO(pqrs);

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getBased()).isEqualTo(pqrs.getBased());
        assertThat(dto.getType()).isEqualTo(PqrsType.QUEJA);
        assertThat(dto.getStatus()).isEqualTo(PqrsStatus.RECIBIDA);
        assertThat(dto.getSubject()).isEqualTo("Producto no llegó");
        assertThat(dto.getDescription()).isEqualTo("El producto nunca llegó a mi dirección");
    }

    @Test
    @DisplayName("toResponseDTO con null: retorna null (comportamiento estándar de MapStruct)")
    void toResponseDTO_nullInput_returnsNull() {
        assertThat(mapper.toResponseDTO(null)).isNull();
    }

    @Test
    @DisplayName("toAdminDetailDTO: además de los campos planos, copia los datos del requester y resuelve su nombre")
    void toAdminDetailDTO_mapsRequesterInfoAndResolvesName() {
        Pqrs pqrs = samplePqrs();
        when(requesterNameResolver.resolve(pqrs.getRequester())).thenReturn("Ana Gómez");

        PqrsAdminDetailDTO dto = mapper.toAdminDetailDTO(pqrs);

        assertThat(dto.getRequesterId()).isEqualTo(1L);
        assertThat(dto.getRequesterEmail()).isEqualTo("consumidor@test.com");
        assertThat(dto.getRequesterPhone()).isEqualTo("3001234567");
        // Este campo no viene de una propiedad plana de Pqrs: lo llena el
        // @AfterMapping invocando a RequesterNameResolver.
        assertThat(dto.getRequesterName()).isEqualTo("Ana Gómez");
    }
}
