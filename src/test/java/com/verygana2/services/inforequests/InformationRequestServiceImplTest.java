package com.verygana2.services.inforequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.inforequests.InformationRequestDTOs.CreateRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ItemRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.RequestView;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.RespondRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.ReviewRequest;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.enums.commercial.CommercialDocumentReviewStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentType;
import com.verygana2.models.enums.inforequests.DeliveryStatus;
import com.verygana2.models.enums.inforequests.InformationRequestStatus;
import com.verygana2.models.enums.inforequests.InformationRequestTrigger;
import com.verygana2.models.enums.inforequests.RequestItemType;
import com.verygana2.models.enums.inforequests.RequestReviewResult;
import com.verygana2.models.inforequests.InformationRequest;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.inforequests.ComplianceAlertRepository;
import com.verygana2.repositories.inforequests.InformationRequestRepository;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.inforequests.InformationRequestService.SystemRequest;

/**
 * Requerimientos (4.8): piden algo concreto, no se duplican, recibir una respuesta no los
 * cierra y la falla de una notificación no se registra como "no respondió".
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InformationRequestServiceImpl")
class InformationRequestServiceImplTest {

    private static final long COMMERCIAL_ID = 7L;
    private static final UUID COMMERCIAL_PUBLIC_ID = new UUID(0, COMMERCIAL_ID);

    @Mock private InformationRequestRepository requestRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private CommercialDocumentRepository documentRepository;
    @Mock private ComplianceAlertRepository alertRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserIdResolver userIdResolver;

    @InjectMocks private InformationRequestServiceImpl service;

    private CommercialDetails commercial;

    @BeforeEach
    void setUp() {
        commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);
        commercial.setCompanyName("Tienda");
        lenient().when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
        lenient().when(userIdResolver.toInternalId(COMMERCIAL_PUBLIC_ID)).thenReturn(COMMERCIAL_ID);
        lenient().when(userIdResolver.toPublicId(COMMERCIAL_ID)).thenReturn(COMMERCIAL_PUBLIC_ID);
        lenient().when(requestRepository.findByOpenDedupKey(anyString())).thenReturn(Optional.empty());
        lenient().when(requestRepository.save(any(InformationRequest.class))).thenAnswer(inv -> {
            InformationRequest r = inv.getArgument(0);
            if (r.getPublicId() == null) {
                r.setPublicId(UUID.randomUUID());
            }
            return r;
        });
    }

    private static ItemRequest item(RequestItemType type, CommercialDocumentType docType) {
        ItemRequest item = new ItemRequest();
        item.setItemType(type);
        item.setDocumentType(docType);
        item.setDescription("Actualice el permiso sectorial de la sede centro");
        return item;
    }

    private SystemRequest system(String key) {
        return new SystemRequest(COMMERCIAL_ID, InformationRequestTrigger.DOCUMENT_EXPIRED, "DOCUMENT_EXPIRY", 1,
                "El documento venció", "4.8", key,
                List.of(item(RequestItemType.DOCUMENT, CommercialDocumentType.PERMISO_SECTORIAL)));
    }

    private InformationRequest openRequest(InformationRequestStatus status) {
        InformationRequest r = new InformationRequest();
        r.setPublicId(UUID.randomUUID());
        r.setCommercial(commercial);
        r.setTrigger(InformationRequestTrigger.INCONSISTENCY);
        r.setReason("Diferencia de dirección");
        r.setRelationToOperation("4.8");
        r.setStatus(status);
        r.setDedupKey("K");
        r.setOpenDedupKey("K");
        return r;
    }

    @Nested
    @DisplayName("apertura")
    class Opening {

        @Test
        @DisplayName("un reintento con la misma clave no duplica el requerimiento")
        void retryDoesNotDuplicate() {
            InformationRequest existing = openRequest(InformationRequestStatus.OPEN);
            when(requestRepository.findByOpenDedupKey("DOCUMENT_EXPIRED:9")).thenReturn(Optional.of(existing));

            assertThat(service.openSystemRequest(system("DOCUMENT_EXPIRED:9"))).isSameAs(existing);
            verify(requestRepository, never()).save(any());
            verify(notificationService, never()).createInternalNotification(anyLong(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("abre con estado OPEN, lo comunica y registra el envío")
        void opensAndCommunicates() {
            InformationRequest opened = service.openSystemRequest(system("DOCUMENT_EXPIRED:9"));

            assertThat(opened.getStatus()).isEqualTo(InformationRequestStatus.OPEN);
            assertThat(opened.getItems()).singleElement()
                    .satisfies(i -> assertThat(i.getDocumentType()).isEqualTo(CommercialDocumentType.PERMISO_SECTORIAL));
            assertThat(opened.getCommunications()).singleElement()
                    .satisfies(c -> assertThat(c.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT));
        }

        @Test
        @DisplayName("si la notificación falla queda FAILED: no se interpreta como falta de respuesta")
        void failedNotificationIsRecorded() {
            doThrow(new RuntimeException("SSE caído")).when(notificationService)
                    .createInternalNotification(anyLong(), anyString(), anyString(), any());

            InformationRequest opened = service.openSystemRequest(system("DOCUMENT_EXPIRED:9"));

            assertThat(opened.getStatus()).isEqualTo(InformationRequestStatus.OPEN);
            assertThat(opened.getCommunications()).singleElement()
                    .satisfies(c -> assertThat(c.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED));
        }

        @Test
        @DisplayName("vincula un documento vigente y aceptado en lugar de exigir otra carga")
        void linksExistingEvidence() {
            CommercialDocument permit = new CommercialDocument();
            permit.setId(44L);
            permit.setDocumentType(CommercialDocumentType.PERMISO_SECTORIAL);
            permit.setStatus(CommercialDocumentStatus.VALIDATED);
            permit.setReviewStatus(CommercialDocumentReviewStatus.ACCEPTED);
            permit.setExpiresAt(LocalDate.now().plusYears(1));
            when(documentRepository.findByCommercial_IdAndStatusInOrderByUploadedAtDesc(any(), any()))
                    .thenReturn(List.of(permit));

            InformationRequest opened = service.openSystemRequest(system("NEW_REQUIREMENT:1"));

            assertThat(opened.getItems().get(0).getLinkedDocumentId()).isEqualTo(44L);
        }

        @Test
        @DisplayName("compliance no puede pedir un documento sin decir cuál")
        void documentItemNeedsType() {
            CreateRequest dto = new CreateRequest();
            dto.setCommercialPublicId(COMMERCIAL_PUBLIC_ID);
            dto.setTrigger(InformationRequestTrigger.NEW_REQUIREMENT);
            dto.setReason("Nuevo requisito sectorial");
            dto.setRelationToOperation("4.8");
            dto.setItems(List.of(item(RequestItemType.DOCUMENT, null)));

            assertThatThrownBy(() -> service.create(dto, 99L)).isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("un requerimiento idéntico abierto no se crea otra vez")
        void identicalOpenRequestIsRejected() {
            CreateRequest dto = new CreateRequest();
            dto.setCommercialPublicId(COMMERCIAL_PUBLIC_ID);
            dto.setTrigger(InformationRequestTrigger.NEW_REQUIREMENT);
            dto.setReason("Nuevo requisito sectorial");
            dto.setRelationToOperation("4.8");
            dto.setItems(List.of(item(RequestItemType.DOCUMENT, CommercialDocumentType.PERMISO_SECTORIAL)));
            when(requestRepository.findByOpenDedupKey(org.mockito.ArgumentMatchers.startsWith("MANUAL:")))
                    .thenReturn(Optional.of(openRequest(InformationRequestStatus.OPEN)));

            assertThatThrownBy(() -> service.create(dto, 99L)).isInstanceOf(InvalidRequestException.class);
            verify(requestRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("respuesta y revisión")
    class ResponseAndReview {

        @Test
        @DisplayName("responder deja el requerimiento RESPONDED: no lo cierra")
        void respondDoesNotClose() {
            InformationRequest request = openRequest(InformationRequestStatus.OPEN);
            when(requestRepository.findByPublicIdAndCommercial_Id(request.getPublicId(), COMMERCIAL_ID))
                    .thenReturn(Optional.of(request));
            RespondRequest dto = new RespondRequest();
            dto.setMessage("La dirección correcta es la del RUT");

            RequestView view = service.respond(COMMERCIAL_ID, request.getPublicId(), dto);

            assertThat(view.status()).isEqualTo(InformationRequestStatus.RESPONDED);
            assertThat(request.getOpenDedupKey()).isEqualTo("K");
            assertThat(view.communications()).isEmpty();
        }

        @Test
        @DisplayName("una respuesta vacía no subsana nada")
        void emptyResponseIsRejected() {
            InformationRequest request = openRequest(InformationRequestStatus.OPEN);
            when(requestRepository.findByPublicIdAndCommercial_Id(request.getPublicId(), COMMERCIAL_ID))
                    .thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.respond(COMMERCIAL_ID, request.getPublicId(), new RespondRequest()))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("no se puede adjuntar un documento que no está vigente en el expediente")
        void supersededDocumentIsRejected() {
            InformationRequest request = openRequest(InformationRequestStatus.OPEN);
            when(requestRepository.findByPublicIdAndCommercial_Id(request.getPublicId(), COMMERCIAL_ID))
                    .thenReturn(Optional.of(request));
            CommercialDocument old = new CommercialDocument();
            old.setId(5L);
            old.setStatus(CommercialDocumentStatus.SUPERSEDED);
            when(documentRepository.findByIdAndCommercial_Id(5L, COMMERCIAL_ID)).thenReturn(Optional.of(old));
            RespondRequest dto = new RespondRequest();
            dto.setDocumentIds(List.of(5L));

            assertThatThrownBy(() -> service.respond(COMMERCIAL_ID, request.getPublicId(), dto))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("revisión suficiente cierra y libera la clave; insuficiente exige nota y queda OBSERVED")
        void reviewOutcomes() {
            InformationRequest request = openRequest(InformationRequestStatus.RESPONDED);
            when(requestRepository.findByPublicId(request.getPublicId())).thenReturn(Optional.of(request));

            ReviewRequest insufficient = new ReviewRequest();
            insufficient.setResult(RequestReviewResult.INSUFFICIENT);
            assertThatThrownBy(() -> service.review(request.getPublicId(), 99L, insufficient))
                    .isInstanceOf(InvalidRequestException.class);

            insufficient.setNotes("El permiso es de otra sede");
            assertThat(service.review(request.getPublicId(), 99L, insufficient).status())
                    .isEqualTo(InformationRequestStatus.OBSERVED);

            request.setStatus(InformationRequestStatus.RESPONDED);
            ReviewRequest sufficient = new ReviewRequest();
            sufficient.setResult(RequestReviewResult.SUFFICIENT);
            assertThat(service.review(request.getPublicId(), 99L, sufficient).status())
                    .isEqualTo(InformationRequestStatus.CLOSED_SATISFIED);
            assertThat(request.getOpenDedupKey()).isNull();
            assertThat(request.getReviews()).hasSize(2);
        }

        @Test
        @DisplayName("no se revisa un requerimiento que no tiene respuesta")
        void cannotReviewWithoutResponse() {
            InformationRequest request = openRequest(InformationRequestStatus.OPEN);
            when(requestRepository.findByPublicId(request.getPublicId())).thenReturn(Optional.of(request));
            ReviewRequest sufficient = new ReviewRequest();
            sufficient.setResult(RequestReviewResult.SUFFICIENT);

            assertThatThrownBy(() -> service.review(request.getPublicId(), 99L, sufficient))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }
}
