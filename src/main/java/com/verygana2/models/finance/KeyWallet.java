package com.verygana2.models.finance;

import java.math.BigDecimal;
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
     * el usuario se registra. La relación inversa se accede vía consumer.getKeyWallet().
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
    @Column(name = "purchase_keys_cents", nullable = false)
    @PositiveOrZero
    @Builder.Default
    private Long purchaseKeysCents = 0L;

    /**
     * Llaves de compra reservadas durante un copago en curso (Copayment.PENDING).
     * No están disponibles para otros gastos hasta que el copago se resuelva.
     * Si Wompi aprueba: se debitan definitivamente (pasan a 0 junto con
     * purchaseKeys).
     * Si Wompi rechaza: se devuelven a purchaseKeys.
     *
     * INVARIANTE: purchaseKeys + blockedPurchaseKeys = saldo real total de compra.
     */
    @Column(name = "blocked_purchase_keys_cents", nullable = false)
    @PositiveOrZero
    @Builder.Default
    private Long blockedPurchaseKeysCents = 0L;

    // ─── LLAVES DE CONECTIVIDAD ───────────────────────────────────────────────

    /**
     * Llaves para canjear recargas y paquetes de datos vía Puntored.
     * Representan el 25% de las llaves promocionales.
     *
     * VENCIMIENTO: todas las connectivity_keys vencen a las 00:00 (medianoche
     * Colombia) del día siguiente al día en que fueron ganadas — sin importar
     * la hora exacta dentro del día en que se ganaron.
     * Ejemplo: si el usuario gana 50 llaves a las 9 AM del 8 de abril y otras
     * 100 a las 11 PM del mismo 8 de abril, AMBAS vencen el 9 de abril a las 00:00.
     * El job calcula expires_at = inicio_del_dia_siguiente en zona Colombia
     * (UTC-5).
     */
    @Column(name = "connectivity_keys_cents", nullable = false)
    @PositiveOrZero
    @Builder.Default
    private Long connectivityKeysCents = 0L;

    /**
     * Llaves de conectividad reservadas durante una recarga en curso.
     * Mismo principio que blockedPurchaseKeys.
     */
    @Column(name = "blocked_connectivity_keys_cents", nullable = false)
    @PositiveOrZero
    @Builder.Default
    private Long blockedConnectivityKeysCents = 0L;

    // ─── AUDITORÍA ────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
        if (this.purchaseKeysCents == null)
            this.purchaseKeysCents = 0L;
        if (this.blockedPurchaseKeysCents == null)
            this.blockedPurchaseKeysCents = 0L;
        if (this.connectivityKeysCents == null)
            this.connectivityKeysCents = 0L;
        if (this.blockedConnectivityKeysCents == null)
            this.blockedConnectivityKeysCents = 0L;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    // ─── CONSULTAS ────────────────────────────────────────────────────────────

    public long getAvailableKeysCents () {
        return purchaseKeysCents + connectivityKeysCents;
    }
 
    public long getTotalPurchaseKeysCents() {
        return purchaseKeysCents + blockedPurchaseKeysCents;
    }
 
    public long getTotalConnectivityKeysCents() {
        return connectivityKeysCents + blockedConnectivityKeysCents;
    }
 
    public boolean hasSufficientPurchaseKeysCents(long amountCents) {
        return purchaseKeysCents >= amountCents;
    }
 
    public boolean hasSufficientConnectivityKeysCents(long amountCents) {
        return connectivityKeysCents >= amountCents;
    }

// ─── OPERACIONES DE COMPRA ────────────────────────────────────────────────

    public static KeyWallet createFor (ConsumerDetails consumer) {
        KeyWallet keyWallet = new KeyWallet();
        keyWallet.setConsumer(consumer);
        return keyWallet;
    }
 
    /**
     * Acredita llaves de compra y conectividad según la distribución 75/25.
     * Llamado cuando el empresario hace un depósito y las llaves se distribuyen.
     */
    public void creditKeysCents(long purchaseAmountCents, long connectivityAmountCents) {
        validateNonNegative(purchaseAmountCents, "purchaseAmountCents");
        validateNonNegative(connectivityAmountCents, "connectivityAmountCents");
        this.purchaseKeysCents += purchaseAmountCents;
        this.connectivityKeysCents += connectivityAmountCents;
    }
 
    /**
     * Reserva llaves de compra cuando inicia un copago (Copayment → PENDING).
     * Genera KeyTransaction(RESERVE_COPAYMENT_PENDING).
     */
    public void reservePurchaseKeysCents(long amountCents) {
        validatePositive(amountCents, "amountCents");
        if (!hasSufficientPurchaseKeysCents(amountCents))
            throw new IllegalStateException(
                "Insufficient purchase keys: availables=" + new BigDecimal(purchaseKeysCents).divide(new BigDecimal(1000)) + ", required=" + new BigDecimal(amountCents).divide(new BigDecimal(1000)));
        this.purchaseKeysCents -= amountCents;
        this.blockedPurchaseKeysCents += amountCents;
    }
 
    /**
     * Confirma el débito de llaves reservadas cuando Wompi aprueba el copago.
     * Genera KeyTransaction(DEBIT_COPAYMENT).
     */
    public void confirmReservedPurchaseKeysCents(long amountCents) {
        validatePositive(amountCents, "amountCents");
        if (this.blockedPurchaseKeysCents < amountCents)
            throw new IllegalStateException("There are not enough blocked keys to confirm");
        this.blockedPurchaseKeysCents -= amountCents;
    }

    /**
     * Devuelve las llaves reservadas al saldo disponible cuando el copago falla.
     * Genera KeyTransaction(RELEASE_COPAYMENT_CANCELLED).
     */
    public void releasePurchaseKeysCents(long amountCents) {
        validatePositive(amountCents, "amountCents");
        if (this.blockedPurchaseKeysCents < amountCents)
            throw new IllegalStateException("There are not enough blocked keys to release");
        this.blockedPurchaseKeysCents -= amountCents;
        this.purchaseKeysCents += amountCents;
    }
 
    // ─── OPERACIONES DE CONECTIVIDAD ──────────────────────────────────────────
 
    /**
     * Reserva llaves de conectividad cuando inicia una recarga.
     */
    public void reserveConnectivityKeysCents(long amountCents) {
        validatePositive(amountCents, "amountCents");
        if (!hasSufficientConnectivityKeysCents(amountCents))
            throw new IllegalStateException(
                "Unsufficient connectivity keys: available=" + new BigDecimal(connectivityKeysCents).divide(new BigDecimal(1000)) + ", required=" + new BigDecimal(amountCents).divide(new BigDecimal(1000)));
        this.connectivityKeysCents -= amountCents;
        this.blockedConnectivityKeysCents += amountCents;
    }
 
    /**
     * Confirma el débito de conectividad cuando Puntored confirma la recarga.
     */
    public void confirmReservedConnectivityKeysCents(long amountCents) {
        validatePositive(amountCents, "amountCents");
        if (this.blockedConnectivityKeysCents < amountCents)
            throw new IllegalStateException("There are not enough blocked connectivity keys");
        this.blockedConnectivityKeysCents -= amountCents;
    }
 
    /**
     * Devuelve llaves de conectividad si Puntored rechaza la recarga.
     */
    public void releaseConnectivityKeysCents(long amountCents) {
        validatePositive(amountCents, "amountCents");
        if (this.blockedConnectivityKeysCents < amountCents)
            throw new IllegalStateException("There are not enough blocked connectivity keys to release");
        this.blockedConnectivityKeysCents -= amountCents;
        this.connectivityKeysCents += amountCents;
    }
 
    // ─── VENCIMIENTOS ────────────────────────────────────────────────────────
 
    /**
     * Expira llaves de compra al corte mensual.
     * Solo expiran las llaves DISPONIBLES, nunca las bloqueadas.
     * Llamado por el job nocturno después de verificar que no hay
     * copagos pendientes con esas llaves.
     *
     * @return cantidad de llaves expiradas (para calcular el COP a mover a FORTIFICATION)
     */
    public long expirePurchaseKeysCents(long amountCents) {
        validatePositive(amountCents, "amountCents");
        if (this.purchaseKeysCents < amountCents)
            throw new IllegalStateException("Cannot expire more purchase keys than availables");
        this.purchaseKeysCents -= amountCents;
        return amountCents;
    }
 
    /**
     * Expira llaves de conectividad al corte diario.
     */
    public long expireConnectivityKeysCents(long amountCents) {
        validatePositive(amountCents, "amountCents");
        if (this.connectivityKeysCents < amountCents)
            throw new IllegalStateException("Cannot expire more connectivity keys than availables");
        this.connectivityKeysCents -= amountCents;
        return amountCents;
    }
 
    // ─── PRIVADOS ─────────────────────────────────────────────────────────────
 
    private void validatePositive(long amountCents, String field) {
        if (amountCents <= 0)
            throw new IllegalArgumentException(field + "Must be positive");
    }
 
    private void validateNonNegative(long amountCents, String field) {
        if (amountCents < 0)
            throw new IllegalArgumentException(field + "Cannot be negative");
    }
}
 