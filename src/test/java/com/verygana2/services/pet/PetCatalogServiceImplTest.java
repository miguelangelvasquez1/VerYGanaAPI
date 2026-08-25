package com.verygana2.services.pet;

import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.dtos.pet.PetCatalogItemResponseDTO;
import com.verygana2.mappers.pet.PetCatalogItemMapper;
import com.verygana2.models.pets.PetCatalogItem;
import com.verygana2.repositories.pet.PetCatalogItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PetCatalogServiceImplTest {

    @Mock private PetCatalogItemRepository catalogRepository;
    @Mock private PetCatalogItemMapper catalogMapper;

    private PetCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PetCatalogServiceImpl(catalogRepository, catalogMapper);
        ReflectionTestUtils.setField(service, "petsCdnDomain", "cdn.pets.test");
        ReflectionTestUtils.setField(service, "petsBucketName", "verygana-pets");

        // El servicio reconstruye la respuesta a partir de la del mapper para inyectarle
        // la url pública; sin este stub devolvería null y reventaría antes de llegar a
        // lo que se está probando.
        when(catalogMapper.toResponseDTO(any(PetCatalogItem.class)))
                .thenAnswer(inv -> {
                    PetCatalogItem item = inv.getArgument(0);
                    return new PetCatalogItemResponseDTO(
                            item.getId(), item.getExternalId(), item.getName(), null,
                            null, null, null, null, null, null,
                            null, null, null, null, null, null, null, item.getActive());
                });
    }

    private PetCatalogItemRequestDTO dto(String spriteObjectKey) {
        return new PetCatalogItemRequestDTO(
                1200, "croquetas", "comida", false, false, false, 50,
                spriteObjectKey,
                0, 0, 0, 0, 0, 0, 0, 0, true);
    }

    /** El repositorio devuelve lo que le llega, que es lo que queremos inspeccionar. */
    private void saveEchoesBack() {
        when(catalogRepository.save(any(PetCatalogItem.class)))
                .thenAnswer(inv -> {
                    PetCatalogItem saved = inv.getArgument(0);
                    if (saved.getId() == null) saved.setId(99L);
                    return saved;
                });
    }

    @Nested
    @DisplayName("createCatalogItem")
    class Create {

        @Test
        @DisplayName("guarda el spriteObjectKey que viene en el DTO")
        void tomaElSpriteDelDto() {
            // La regresión: esto pasaba null fijo, así que el CRUD del diseñador creaba
            // todos los ítems sin imagen por más que el asset se hubiera subido bien.
            when(catalogMapper.toEntity(any())).thenReturn(new PetCatalogItem());
            saveEchoesBack();

            PetCatalogItemResponseDTO res = service.createCatalogItem(dto("catalog-sprites/6/abc"));

            assertThat(res.spriteUrl()).isEqualTo("https://cdn.pets.test/catalog-sprites/6/abc");
        }

        @Test
        @DisplayName("sin externalId el servidor asigna el siguiente")
        void asignaExternalIdAutomatico() {
            // Lo tecleaba el diseñador. Dos ítems con el mismo número hacen que
            // findByExternalId (que devuelve Optional) reviente, y toda compra de ese
            // ítem pasa a dar 500.
            when(catalogMapper.toEntity(any())).thenReturn(new PetCatalogItem());
            when(catalogRepository.nextExternalId()).thenReturn(1301);
            saveEchoesBack();

            assertThat(service.createCatalogItem(dto(null)).externalId()).isEqualTo(1301);
        }

        @Test
        @DisplayName("si el DTO trae externalId se respeta y no se pide otro")
        void respetaElExternalIdDelDto() {
            // Hace falta para mapear un ítem nuestro sobre uno horneado en el build.
            PetCatalogItem entidad = new PetCatalogItem();
            entidad.setExternalId(7);
            when(catalogMapper.toEntity(any())).thenReturn(entidad);
            saveEchoesBack();

            assertThat(service.createCatalogItem(dto(null)).externalId()).isEqualTo(7);
            verify(catalogRepository, org.mockito.Mockito.never()).nextExternalId();
        }

        @Test
        @DisplayName("sin sprite el ítem se crea igual, con la url vacía")
        void sinSprite() {
            when(catalogMapper.toEntity(any())).thenReturn(new PetCatalogItem());
            saveEchoesBack();

            assertThat(service.createCatalogItem(dto(null)).spriteUrl()).isEmpty();
        }

        @Test
        @DisplayName("la sobrecarga con clave explícita gana sobre la del DTO")
        void claveExplicitaGana() {
            // La usa el flujo de publicar una solicitud del comercial, donde el sprite
            // sale de la solicitud y no de lo que mande el cliente.
            when(catalogMapper.toEntity(any())).thenReturn(new PetCatalogItem());
            saveEchoesBack();

            PetCatalogItemResponseDTO res =
                    service.createCatalogItem(dto("del-dto"), "catalog-sprites/6/explicita");

            assertThat(res.spriteUrl()).isEqualTo("https://cdn.pets.test/catalog-sprites/6/explicita");
        }
    }

    @Nested
    @DisplayName("updateCatalogItem")
    class Update {

        private PetCatalogItem existente(String spriteObjectKey) {
            PetCatalogItem item = new PetCatalogItem();
            item.setId(7L);
            item.setSpriteObjectKey(spriteObjectKey);
            return item;
        }

        @Test
        @DisplayName("cambia el sprite cuando la edición trae uno nuevo")
        void aplicaSpriteNuevo() {
            when(catalogRepository.findById(7L)).thenReturn(Optional.of(existente("vieja.png")));
            saveEchoesBack();

            PetCatalogItemResponseDTO res =
                    service.updateCatalogItem(7L, dto("catalog-sprites/6/nueva.png"));

            assertThat(res.spriteUrl()).isEqualTo("https://cdn.pets.test/catalog-sprites/6/nueva.png");
        }

        @Test
        @DisplayName("una edición que no toca la imagen no la borra")
        void noPisaConNull() {
            // Cambiar solo el precio mandaba spriteObjectKey vacío; si se aplicara sin
            // más, el ítem perdería el sprite que ya tenía.
            when(catalogRepository.findById(7L)).thenReturn(Optional.of(existente("catalog-sprites/6/vieja.png")));
            saveEchoesBack();

            assertThat(service.updateCatalogItem(7L, dto(null)).spriteUrl())
                    .isEqualTo("https://cdn.pets.test/catalog-sprites/6/vieja.png");
            assertThat(service.updateCatalogItem(7L, dto("   ")).spriteUrl())
                    .isEqualTo("https://cdn.pets.test/catalog-sprites/6/vieja.png");
        }
    }
}
