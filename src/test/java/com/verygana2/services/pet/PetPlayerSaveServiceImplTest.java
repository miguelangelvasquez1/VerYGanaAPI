package com.verygana2.services.pet;

import com.verygana2.dtos.pet.PetSaveDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.pets.PetPlayerSave;
import com.verygana2.repositories.pet.PetPlayerSaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PetPlayerSaveServiceImpl")
class PetPlayerSaveServiceImplTest {

    private static final int MAX = 64;

    @Mock private PetPlayerSaveRepository repository;
    private PetPlayerSaveServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PetPlayerSaveServiceImpl(repository);
        ReflectionTestUtils.setField(service, "maxSizeBytes", MAX);
    }

    private void saveEchoesBack() {
        when(repository.save(any(PetPlayerSave.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("sin guardado previo devuelve null, no un blob vacío")
    void sinGuardado() {
        // El controlador lo traduce a 204: el juego necesita distinguir "nunca guardé"
        // de "guardé algo vacío" para saber si arranca una mascota nueva.
        when(repository.findById(7L)).thenReturn(Optional.empty());
        assertThat(service.get(7L)).isNull();
    }

    @Test
    @DisplayName("crea el guardado la primera vez y lo reemplaza después")
    void creaYReemplaza() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        saveEchoesBack();
        assertThat(service.save(7L, "{\"lvl\":1}").data()).isEqualTo("{\"lvl\":1}");

        PetPlayerSave existente = new PetPlayerSave(7L, "{\"lvl\":1}");
        existente.setUpdatedAt(ZonedDateTime.now().minusDays(1));
        when(repository.findById(7L)).thenReturn(Optional.of(existente));

        PetSaveDTO res = service.save(7L, "{\"lvl\":2}");

        assertThat(res.data()).isEqualTo("{\"lvl\":2}");
        assertThat(existente.getUpdatedAt()).isAfter(ZonedDateTime.now().minusMinutes(1));
    }

    @Test
    @DisplayName("rechaza un guardado por encima del máximo sin tocar la base")
    void rechazaDemasiadoGrande() {
        // Sin tope, un PUT repetido con basura llena la tabla: el contenido no se
        // valida porque es opaco, así que el tamaño es la única defensa.
        assertThatThrownBy(() -> service.save(7L, "x".repeat(MAX + 1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("máximo");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("rechaza vacío o nulo")
    void rechazaVacio() {
        assertThatThrownBy(() -> service.save(7L, "  ")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.save(7L, null)).isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("el tope se mide en bytes UTF-8, no en caracteres")
    void topeEnBytes() {
        // Un guardado con acentos o emoji ocupa más bytes que caracteres; medir en
        // length() dejaría pasar blobs por encima del límite real de la columna.
        String conAcentos = "á".repeat(MAX);   // 2 bytes cada una
        assertThatThrownBy(() -> service.save(7L, conAcentos))
                .isInstanceOf(InvalidRequestException.class);
    }
}
