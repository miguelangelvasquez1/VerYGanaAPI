package com.verygana2.services.interfaces.recognitions;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.recognitions.ConsumerRecognitionDTO;
import com.verygana2.dtos.recognitions.CreateRecognitionRequestDTO;
import com.verygana2.dtos.recognitions.RecognitionCandidateDTO;
import com.verygana2.dtos.recognitions.RecognitionDetailDTO;
import com.verygana2.dtos.recognitions.RecognitionFundDTO;
import com.verygana2.dtos.recognitions.RecognitionSummaryDTO;
import com.verygana2.models.enums.recognitions.RecognitionCriterion;
import com.verygana2.models.enums.recognitions.RecognitionStatus;

/**
 * Reconocimientos de VERYGANA (Contrato B 10.16 y 5.9): un admin le compra un producto a un
 * empresario con el fondo de fortalecimiento y se lo entrega a un usuario destacado, que no
 * paga dinero ni llaves. Para el empresario es una venta normal: Prosperidad, comisión de su
 * vocación y payout diario.
 */
public interface RecognitionService {

    /**
     * Compra y entrega en una sola transacción. Si el fondo no cubre el precio completo, si no
     * hay stock o si algo falla, no queda nada: ni stock reservado, ni compra, ni asiento.
     *
     * @param idempotencyKey clave de origen: el mismo valor devuelve el reconocimiento ya creado
     */
    RecognitionDetailDTO create(CreateRecognitionRequestDTO request, Long adminId, String idempotencyKey);

    RecognitionDetailDTO get(UUID publicId);

    Page<RecognitionSummaryDTO> search(RecognitionStatus status, UUID beneficiaryPublicId, UUID commercialPublicId,
            ZonedDateTime from, ZonedDateTime to, Pageable pageable);

    /**
     * Anula un reconocimiento cuyos ítems aún no se pagaron al comercio: el precio vuelve al
     * fondo, la comisión se revierte y el Saldo de Prosperidad recupera lo absorbido.
     */
    RecognitionDetailDTO cancel(UUID publicId, String cause, Long adminId);

    /** Ranking de usuarios por criterio. El periodo no aplica a XP. */
    Page<RecognitionCandidateDTO> candidates(RecognitionCriterion criterion, ZonedDateTime from, ZonedDateTime to,
            Pageable pageable);

    RecognitionFundDTO fund();

    /** Reconocimientos recibidos por un consumidor. */
    Page<ConsumerRecognitionDTO> receivedBy(Long consumerId, Pageable pageable);

    /**
     * Verifica que lo que salió del fondo por cada reconocimiento coincida con los pagos de
     * VERYGANA registrados como componentes de venta. Devuelve cuántos recursos no cuadran.
     */
    int reconcileFunding();
}
