package com.verygana2.services.interfaces.levels;



import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.levels.LevelProfileResponse;
import com.verygana2.dtos.levels.TransactionLogResponse;
import com.verygana2.models.enums.ActivityType;
import com.verygana2.models.levels.UserLevelProfile;

public interface LevelService {

    /**
     * Crea el perfil de nivel al registrarse un nuevo ConsumerDetails.
     */
    UserLevelProfile initializeProfile(Long consumerId);

    /**
     * Registra XP por una actividad estándar aplicando el multiplicador del nivel.
     * NO toca el KeyWallet — cada servicio maneja sus propias llaves.
     */
    UserLevelProfile awardActivity(Long consumerId, ActivityType activityType);

    /**
     * Retorna el multiplicador del nivel actual del usuario.
     * Usado por otros servicios para escalar llaves antes de acreditar al KeyWallet.
     */
    double getMultiplier(Long consumerId);

    /**
     * Pausa los beneficios activos del usuario y activa misión de reactivación.
     * Invocado por InactivityScheduler al día 31.
     */
    void pauseBenefits(Long consumerId);

    /**
     * Crea la misión de reactivación: ganar XP suficiente en 7 días.
     * Idempotente — no crea duplicados.
     */
    void triggerReactivationMission(Long consumerId);

    /**
     * Devuelve el perfil de nivel con toda la información para el frontend.
     */
    LevelProfileResponse getProfileResponse(Long consumerId);

    /**
     * Historial paginado de transacciones XP.
     */
    Page<TransactionLogResponse> getTransactionHistory(Long consumerId, Pageable pageable);
}