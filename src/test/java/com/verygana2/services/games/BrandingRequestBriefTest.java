package com.verygana2.services.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.verygana2.dtos.branding.ApproveBrandingRequestDTO;
import com.verygana2.models.User;
import com.verygana2.models.branding.BrandingRequest;
import com.verygana2.models.branding.CorporateResource;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.games.Game;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.repositories.branding.BrandingRequestCommentRepository;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.details.AdminDetailsRepository;
import com.verygana2.repositories.details.GameDesignerDetailsRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.utils.games.GameBriefCatalog;
import com.verygana2.utils.games.GameBriefNumbering;
import com.verygana2.utils.games.GameBriefValidator;

import jakarta.validation.ValidationException;

/**
 * El contenido de marca que entrega el anunciante: cuándo se puede guardar, qué
 * bloquea y cómo llega al diseñador.
 *
 * El problema que cierra esto es que hasta ahora nadie le pedía a la marca las
 * preguntas de la trivia ni las palabras de la sopa de letras, pero el esquema sí se
 * las exige al diseñador para poder entregar. El diseñador las inventaba.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Contenido de marca del anunciante")
class BrandingRequestBriefTest {

    @Mock private BrandingRequestRepository brandingRequestRepository;
    @Mock private BrandingRequestCommentRepository commentRepository;
    @Mock private AdminDetailsRepository adminDetailsRepository;
    @Mock private GameDesignerDetailsRepository gameDesignerDetailsRepository;
    @Mock private EmailService emailService;
    @Mock private NotificationService notificationService;
    @Mock private GameBriefValidator gameBriefValidator;
    @Spy private GameBriefCatalog gameBriefCatalog = new GameBriefCatalog();
    @Spy private GameBriefNumbering gameBriefNumbering = new GameBriefNumbering();

    @InjectMocks private BrandingRequestServiceImpl service;

    private static final Map<String, Object> BRIEF =
        Map.of("game", Map.of("questions", List.of(Map.of("id", 1, "question", "¿?"))));

    private BrandingRequest request;

    @BeforeEach
    void setUp() {
        request = BrandingRequest.builder()
            .id(7L)
            .brandName("Coca Cola")
            .status(BrandingRequestStatus.DRAFT)
            .game(Game.builder().id(19L).title("Trivia Quiz").url("trivia-quiz").build())
            .build();

        when(brandingRequestRepository.findByIdAndCommercialUserId(7L, 3L)).thenReturn(Optional.of(request));
        when(brandingRequestRepository.findById(7L)).thenReturn(Optional.of(request));
    }

    @Nested
    @DisplayName("guardar")
    class Save {

        @Test
        @DisplayName("valida contra el esquema del juego y guarda")
        void validatesAndStores() {
            service.saveBrief(7L, 3L, BRIEF);

            verify(gameBriefValidator).validateOrThrow(request.getGame(), BRIEF);
            assertThat(request.getBriefData()).isEqualTo(BRIEF);
        }

        @Test
        @DisplayName("los ids de las preguntas los pone el backend, no el anunciante")
        @SuppressWarnings("unchecked")
        void numbersQuestions() {
            // El formulario no pide ese campo: es un número interno del juego. Si lo
            // llevara la marca, insertar una pregunta en el medio repetiría un id.
            service.saveBrief(7L, 3L, Map.of("game", Map.of("questions", List.of(
                Map.of("question", "¿Una?"), Map.of("question", "¿Dos?")))));

            List<Map<String, Object>> guardadas = (List<Map<String, Object>>)
                ((Map<String, Object>) request.getBriefData().get("game")).get("questions");

            assertThat(guardadas).extracting(q -> q.get("id")).containsExactly(1, 2);
        }

        @Test
        @DisplayName("un contenido inválido no se guarda a medias")
        void invalidContentIsNotStored() {
            // Guardar primero y validar después dejaría en la solicitud un contenido que
            // el envío va a rechazar, sin que el anunciante entienda por qué.
            doThrow(new ValidationException("faltan preguntas"))
                .when(gameBriefValidator).validateOrThrow(any(), any());

            assertThatThrownBy(() -> service.saveBrief(7L, 3L, BRIEF))
                .isInstanceOf(ValidationException.class);

            assertThat(request.getBriefData()).isNull();
        }

        @Test
        @DisplayName("fuera del borrador ya no se puede tocar")
        void onlyInDraft() {
            // Después de enviar, el contenido ya viajó al diseñador: cambiarlo por debajo
            // deja el diseño y la solicitud contando cosas distintas.
            request.setStatus(BrandingRequestStatus.DESIGN_IN_PROGRESS);

            assertThatThrownBy(() -> service.saveBrief(7L, 3L, BRIEF))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("borrador");

            verifyNoInteractions(gameBriefValidator);
        }
    }

    @Nested
    @DisplayName("enviar a revisión")
    class Submit {

        @Test
        @DisplayName("sin el contenido del juego no sale de borrador")
        void blockedWithoutContent() {
            doThrow(new ValidationException("Para Trivia Quiz hay que enviar el contenido"))
                .when(gameBriefValidator).validateOrThrow(any(), any());

            assertThatThrownBy(() -> service.submitForReview(7L, 3L, "una nota"))
                .isInstanceOf(ValidationException.class);

            assertThat(request.getStatus()).isEqualTo(BrandingRequestStatus.DRAFT);
            // El rechazo cancela también el comentario con las notas: queda anotado
            // porque es el efecto colateral que el anunciante no ve venir.
            verifyNoInteractions(commentRepository);
        }

        @Test
        @DisplayName("con el contenido cargado sigue su curso")
        void passesWithContent() {
            request.setBriefData(BRIEF);

            service.submitForReview(7L, 3L, null);

            assertThat(request.getStatus()).isEqualTo(BrandingRequestStatus.PENDING_REVIEW);
        }
    }

    @Nested
    @DisplayName("archivos que audita el diseñador")
    class Resources {

        private BrandingRequest memoryMatch(int validados) {
            BrandingRequest req = BrandingRequest.builder()
                .id(8L)
                .brandName("Coca Cola")
                .status(BrandingRequestStatus.DRAFT)
                .game(Game.builder().id(15L).title("Memory Match").url("memory-match").build())
                .corporateResources(new java.util.ArrayList<>())
                .build();

            for (int i = 0; i < validados; i++) {
                req.getCorporateResources().add(
                    CorporateResource.builder().status(AssetStatus.VALIDATED).build());
            }

            when(brandingRequestRepository.findByIdAndCommercialUserId(8L, 3L)).thenReturn(Optional.of(req));
            return req;
        }

        @Test
        @DisplayName("el memoria no sale con menos imágenes de las que arma en parejas")
        void notEnoughFiles() {
            // El diseñador se enteraba al abrir el diseño, con la solicitud ya en la cola
            // y el presupuesto ya reservado.
            BrandingRequest req = memoryMatch(3);

            assertThatThrownBy(() -> service.submitForReview(8L, 3L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("25")
                .hasMessageContaining("Van 3");

            assertThat(req.getStatus()).isEqualTo(BrandingRequestStatus.DRAFT);
        }

        @Test
        @DisplayName("los archivos a medio subir no cuentan")
        void pendingFilesDoNotCount() {
            // Un recurso en PENDING es un permiso de subida que puede no haberse usado:
            // contarlo dejaría pasar una solicitud sin las imágenes.
            BrandingRequest req = memoryMatch(24);
            req.getCorporateResources().add(
                CorporateResource.builder().status(AssetStatus.PENDING).build());

            assertThatThrownBy(() -> service.submitForReview(8L, 3L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Van 24");
        }

        @Test
        @DisplayName("con las 25 sigue su curso")
        void enoughFiles() {
            BrandingRequest req = memoryMatch(25);

            service.submitForReview(8L, 3L, null);

            assertThat(req.getStatus()).isEqualTo(BrandingRequestStatus.PENDING_REVIEW);
        }

        @Test
        @DisplayName("un juego que no pide archivos no se bloquea por no tenerlos")
        void gamesWithoutRequirementAreNotBlocked() {
            request.setBriefData(BRIEF);

            service.submitForReview(7L, 3L, null);

            assertThat(request.getStatus()).isEqualTo(BrandingRequestStatus.PENDING_REVIEW);
        }
    }

    @Nested
    @DisplayName("aprobar y asignar diseñador")
    class Approve {

        private ApproveBrandingRequestDTO approval() {
            User designerUser = new User();
            designerUser.setId(9L);
            designerUser.setEmail("disenador@verygana.com");

            GameDesignerDetails designer = new GameDesignerDetails();
            designer.setUser(designerUser);
            designer.setName("Ana");
            designer.setLastName("Díaz");
            designer.setActive(true);

            UUID designerPublicId = UUID.fromString("00000000-0000-0000-0000-000000000009");
            when(gameDesignerDetailsRepository.findByPublicId(designerPublicId)).thenReturn(Optional.of(designer));
            when(adminDetailsRepository.findById(1L)).thenReturn(Optional.of(new AdminDetails()));

            ApproveBrandingRequestDTO dto = new ApproveBrandingRequestDTO();
            dto.setDesignerPublicId(designerPublicId);
            return dto;
        }

        @Test
        @DisplayName("siembra el borrador del diseñador con el contenido del anunciante")
        void seedsTheDesignerDraft() {
            // Sin esto el diseñador abre el formulario vacío y tiene que copiar las
            // preguntas a mano desde otra pantalla.
            request.setStatus(BrandingRequestStatus.PENDING_REVIEW);
            request.setBriefData(BRIEF);

            service.approveBrandingRequest(7L, approval(), 1L);

            assertThat(request.getDraftFormData()).isEqualTo(BRIEF);
        }

        @Test
        @DisplayName("no pisa un borrador que el diseñador ya empezó")
        void doesNotOverwriteExistingDraft() {
            Map<String, Object> enCurso = Map.of("game", Map.of("questions", List.of()), "branding", Map.of());
            request.setStatus(BrandingRequestStatus.PENDING_REVIEW);
            request.setBriefData(BRIEF);
            request.setDraftFormData(enCurso);

            service.approveBrandingRequest(7L, approval(), 1L);

            assertThat(request.getDraftFormData()).isEqualTo(enCurso);
        }

        @Test
        @DisplayName("un juego sin contenido de marca no deja el borrador a medias")
        void noBriefLeavesDraftUntouched() {
            request.setStatus(BrandingRequestStatus.PENDING_REVIEW);

            service.approveBrandingRequest(7L, approval(), 1L);

            assertThat(request.getDraftFormData()).isNull();
            // Y la aprobación sigue igual de normal: el diseñador recibe su aviso.
            verify(emailService).sendBrandingDesignerAssignedEmail(any(), any(), any(), any(), any());
        }
    }
}
