package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.verygana2.models.userDetails.ConsumerDetails;

@Entity
@Table(name = "key_wallets")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KeyWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * Relación 1-a-1 con el consumidor. Se crea automáticamente cuando
     * el usuario se registra. No es bidireccional: para buscar el wallet
     * de un usuario se usa keyWalletRepo.findByConsumer(consumer).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false, unique = true)
    @NotNull
    private ConsumerDetails consumer;

    /**
     * Llaves para comprar productos en el marketplace y canjear bonos/mascotas.
     * Representan el 75% de las llaves promocionales distribuidas al usuario.
     *
     * VENCIMIENTO: todas las purchase_keys vencen a las 00:00 (medianoche Colombia)
     * del día siguiente a la fecha de corte mensual definida por la app.
     * El job nocturno calcula la suma de los KeyTransaction con tipo
     * CREDIT_INTERACTION/CREDIT_BUSINESS_DEPOSIT cuyo expires_at < NOW() y
     * aún no están procesados (expiry_processed = false), luego debita ese
     * total del saldo aquí y mueve el valor equivalente en COP al fondo
     * de fortalecimiento en TreasuryAccount(FORTIFICATION).
     */
    @Column(name = "purchase_keys", nullable = false)
    @PositiveOrZero
    @Builder.Default
    private Long purchaseKeys = 0L;

    /**
     * Llaves para canjear recargas y paquetes de datos vía Puntored.
     * Representan el 25% de las llaves promocionales.
     *
     * VENCIMIENTO: todas las connectivity_keys vencen a las 00:00 (medianoche
     * Colombia) del día siguiente al día en que fueron ganadas — sin importar
     * la hora exacta dentro del día en que se ganaron.
     * Ejemplo: si el usuario gana 50 llaves a las 9 AM del 8 de abril y otras
     * 100 a las 11 PM del mismo 8 de abril, AMBAS vencen el 9 de abril a las 00:00.
     * El job calcula expires_at = inicio_del_dia_siguiente en zona Colombia (UTC-5).
     */
    @Column(name = "connectivity_keys", nullable = false)
    @PositiveOrZero
    @Builder.Default
    private Long connectivityKeys = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
        if (this.purchaseKeys == null) this.purchaseKeys = 0L;
        if (this.connectivityKeys == null) this.connectivityKeys = 0L;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}