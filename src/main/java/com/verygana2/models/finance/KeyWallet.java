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
    @Column(name = "purchase_keys", nullable = false)
    @PositiveOrZero
    @Builder.Default
    private Long purchaseKeys = 0L;

    /**
     * Llaves de compra reservadas durante un copago en curso (Copayment.PENDING).
     * No están disponibles para otros gastos hasta que el copago se resuelva.
     * Si Wompi aprueba: se debitan definitivamente (pasan a 0 junto con
     * purchaseKeys).
     * Si Wompi rechaza: se devuelven a purchaseKeys.
     *
     * INVARIANTE: purchaseKeys + blockedPurchaseKeys = saldo real total de compra.
     */
    @Column(name = "blocked_purchase_keys", nullable = false)
    @PositiveOrZero
    @Builder.Default
    private Long blockedPurchaseKeys = 0L;

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
    @Column(name = "connectivity_keys", nullable = false)
    @PositiveOrZero
    @Builder.Default
    private Long connectivityKeys = 0L;

    /**
     * Llaves de conectividad reservadas durante una recarga en curso.
     * Mismo principio que blockedPurchaseKeys.
     */
    @Column(name = "blocked_connectivity_keys", nullable = false)
    @PositiveOrZero
    @Builder.Default
    private Long blockedConnectivityKeys = 0L;

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
        if (this.purchaseKeys == null)
            this.purchaseKeys = 0L;
        if (this.blockedPurchaseKeys == null)
            this.blockedPurchaseKeys = 0L;
        if (this.connectivityKeys == null)
            this.connectivityKeys = 0L;
        if (this.blockedConnectivityKeys == null)
            this.blockedConnectivityKeys = 0L;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    // ─── CONSULTAS ────────────────────────────────────────────────────────────

    public long getAvailableKeys () {
        return purchaseKeys + connectivityKeys;
    }

    public long getTotalKeys() {
        return getTotalPurchaseKeys() + getTotalConnectivityKeys();
    }
 
    /**
     * Total de llaves de compra incluyendo las bloqueadas.
     * Útil para mostrar al usuario su saldo "real" vs "disponible".
     */
    public long getTotalPurchaseKeys() {
        return purchaseKeys + blockedPurchaseKeys;
    }
 
    public long getTotalConnectivityKeys() {
        return connectivityKeys + blockedConnectivityKeys;
    }
 
    public boolean hasSufficientPurchaseKeys(long amount) {
        return purchaseKeys >= amount;
    }
 
    public boolean hasSufficientConnectivityKeys(long amount) {
        return connectivityKeys >= amount;
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
    public void creditKeys(long purchaseAmount, long connectivityAmount) {
        validateNonNegative(purchaseAmount, "purchaseAmount");
        validateNonNegative(connectivityAmount, "connectivityAmount");
        this.purchaseKeys += purchaseAmount;
        this.connectivityKeys += connectivityAmount;
    }
 
    /**
     * Reserva llaves de compra cuando inicia un copago (Copayment → PENDING).
     * Genera KeyTransaction(RESERVE_COPAYMENT_PENDING).
     */
    public void reservePurchaseKeys(long amount) {
        validatePositive(amount, "amount");
        if (!hasSufficientPurchaseKeys(amount))
            throw new IllegalStateException(
                "Llaves insuficientes: disponibles=" + purchaseKeys + ", requeridas=" + amount);
        this.purchaseKeys -= amount;
        this.blockedPurchaseKeys += amount;
    }
 
    /**
     * Confirma el débito de llaves reservadas cuando Wompi aprueba el copago.
     * Genera KeyTransaction(DEBIT_COPAYMENT).
     */
    public void confirmReservedPurchaseKeys(long amount) {
        validatePositive(amount, "amount");
        if (this.blockedPurchaseKeys < amount)
            throw new IllegalStateException("No hay suficientes llaves bloqueadas para confirmar");
        this.blockedPurchaseKeys -= amount;
    }
 
    /**
     * Devuelve las llaves reservadas al saldo disponible cuando el copago falla.
     * Genera KeyTransaction(RELEASE_COPAYMENT_CANCELLED).
     */
    public void releasePurchaseKeys(long amount) {
        validatePositive(amount, "amount");
        if (this.blockedPurchaseKeys < amount)
            throw new IllegalStateException("No hay suficientes llaves bloqueadas para liberar");
        this.blockedPurchaseKeys -= amount;
        this.purchaseKeys += amount;
    }
 
    // ─── OPERACIONES DE CONECTIVIDAD ──────────────────────────────────────────
 
    /**
     * Reserva llaves de conectividad cuando inicia una recarga.
     */
    public void reserveConnectivityKeys(long amount) {
        validatePositive(amount, "amount");
        if (!hasSufficientConnectivityKeys(amount))
            throw new IllegalStateException(
                "Llaves de conectividad insuficientes: disponibles=" + connectivityKeys + ", requeridas=" + amount);
        this.connectivityKeys -= amount;
        this.blockedConnectivityKeys += amount;
    }
 
    /**
     * Confirma el débito de conectividad cuando Puntored confirma la recarga.
     */
    public void confirmReservedConnectivityKeys(long amount) {
        validatePositive(amount, "amount");
        if (this.blockedConnectivityKeys < amount)
            throw new IllegalStateException("No hay suficientes llaves de conectividad bloqueadas");
        this.blockedConnectivityKeys -= amount;
    }
 
    /**
     * Devuelve llaves de conectividad si Puntored rechaza la recarga.
     */
    public void releaseConnectivityKeys(long amount) {
        validatePositive(amount, "amount");
        if (this.blockedConnectivityKeys < amount)
            throw new IllegalStateException("No hay suficientes llaves de conectividad bloqueadas para liberar");
        this.blockedConnectivityKeys -= amount;
        this.connectivityKeys += amount;
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
    public long expirePurchaseKeys(long amount) {
        validatePositive(amount, "amount");
        if (this.purchaseKeys < amount)
            throw new IllegalStateException("No se pueden expirar más llaves de las disponibles");
        this.purchaseKeys -= amount;
        return amount;
    }
 
    /**
     * Expira llaves de conectividad al corte diario.
     */
    public long expireConnectivityKeys(long amount) {
        validatePositive(amount, "amount");
        if (this.connectivityKeys < amount)
            throw new IllegalStateException("No se pueden expirar más llaves de las disponibles");
        this.connectivityKeys -= amount;
        return amount;
    }
 
    // ─── PRIVADOS ─────────────────────────────────────────────────────────────
 
    private void validatePositive(long amount, String field) {
        if (amount <= 0)
            throw new IllegalArgumentException(field + " debe ser positivo");
    }
 
    private void validateNonNegative(long amount, String field) {
        if (amount < 0)
            throw new IllegalArgumentException(field + " no puede ser negativo");
    }
}
 