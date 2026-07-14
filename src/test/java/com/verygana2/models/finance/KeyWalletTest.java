package com.verygana2.models.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la entidad {@link KeyWallet}: reserva/confirmación/liberación de
 * llaves de compra durante un copago, y el mismo ciclo para conectividad.
 * Es el libro de saldo en memoria — cada invariante aquí protege contra
 * gastar llaves que no existen o liberar más de lo reservado.
 */
@DisplayName("KeyWallet (entidad)")
class KeyWalletTest {

    @Nested
    @DisplayName("reservePurchaseKeysCents / confirmReservedPurchaseKeysCents / releasePurchaseKeysCents")
    class PurchaseKeysLifecycle {

        @Test
        @DisplayName("reservar mueve de purchaseKeysCents a blockedPurchaseKeysCents sin cambiar el total")
        void reserve_movesToBlocked() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L).blockedPurchaseKeysCents(0L).build();

            wallet.reservePurchaseKeysCents(30L);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(70L);
            assertThat(wallet.getBlockedPurchaseKeysCents()).isEqualTo(30L);
            assertThat(wallet.getTotalPurchaseKeysCents()).isEqualTo(100L);
        }

        @Test
        @DisplayName("reservar más de lo disponible: lanza IllegalStateException")
        void reserve_insufficientBalance_throws() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(10L).build();

            assertThatThrownBy(() -> wallet.reservePurchaseKeysCents(20L)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("confirmar el débito reduce blockedPurchaseKeysCents definitivamente")
        void confirm_reducesBlockedPermanently() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(70L).blockedPurchaseKeysCents(30L).build();

            wallet.confirmReservedPurchaseKeysCents(30L);

            assertThat(wallet.getBlockedPurchaseKeysCents()).isZero();
            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(70L); // no vuelve al saldo disponible
        }

        @Test
        @DisplayName("liberar (copago rechazado) devuelve las llaves bloqueadas al saldo disponible")
        void release_returnsToAvailableBalance() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(70L).blockedPurchaseKeysCents(30L).build();

            wallet.releasePurchaseKeysCents(30L);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(100L);
            assertThat(wallet.getBlockedPurchaseKeysCents()).isZero();
        }

        @Test
        @DisplayName("confirmar/liberar más de lo bloqueado: lanza IllegalStateException")
        void confirmOrRelease_moreThanBlocked_throws() {
            KeyWallet wallet = KeyWallet.builder().blockedPurchaseKeysCents(10L).build();

            assertThatThrownBy(() -> wallet.confirmReservedPurchaseKeysCents(20L)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> wallet.releasePurchaseKeysCents(20L)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("monto no positivo: lanza IllegalArgumentException")
        void nonPositiveAmount_throwsIllegalArgumentException() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L).build();

            assertThatThrownBy(() -> wallet.reservePurchaseKeysCents(0L)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> wallet.reservePurchaseKeysCents(-5L)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("creditKeysCents: acredita compra y conectividad según los montos recibidos (distribución 75/25 ya calculada afuera)")
    void creditKeys_addsToPurchaseAndConnectivity() {
        KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(0L).connectivityKeysCents(0L).build();

        wallet.creditKeysCents(75L, 25L);

        assertThat(wallet.getPurchaseKeysCents()).isEqualTo(75L);
        assertThat(wallet.getConnectivityKeysCents()).isEqualTo(25L);
    }

    @Test
    @DisplayName("creditKeysCents con monto negativo: lanza IllegalArgumentException")
    void creditKeys_negativeAmount_throws() {
        KeyWallet wallet = KeyWallet.builder().build();

        assertThatThrownBy(() -> wallet.creditKeysCents(-1L, 0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("expirePurchaseKeysCents / expireConnectivityKeysCents: solo debitan lo disponible, nunca lo bloqueado")
    void expireKeys_debitsOnlyAvailable() {
        KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(50L).connectivityKeysCents(20L).build();

        long expiredPurchase = wallet.expirePurchaseKeysCents(30L);
        long expiredConnectivity = wallet.expireConnectivityKeysCents(20L);

        assertThat(expiredPurchase).isEqualTo(30L);
        assertThat(wallet.getPurchaseKeysCents()).isEqualTo(20L);
        assertThat(expiredConnectivity).isEqualTo(20L);
        assertThat(wallet.getConnectivityKeysCents()).isZero();
    }

    @Test
    @DisplayName("expirar más de lo disponible: lanza IllegalStateException")
    void expireKeys_moreThanAvailable_throws() {
        KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(10L).build();

        assertThatThrownBy(() -> wallet.expirePurchaseKeysCents(20L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getAvailableKeysCents: suma purchase + connectivity, sin contar lo bloqueado")
    void getAvailableKeys_sumsPurchaseAndConnectivityOnly() {
        KeyWallet wallet = KeyWallet.builder()
                .purchaseKeysCents(50L).blockedPurchaseKeysCents(999L)
                .connectivityKeysCents(20L).blockedConnectivityKeysCents(999L)
                .build();

        assertThat(wallet.getAvailableKeysCents()).isEqualTo(70L);
    }

    @Test
    @DisplayName("hasSufficientPurchaseKeysCents / hasSufficientConnectivityKeysCents: comparan solo contra el saldo disponible")
    void hasSufficientKeys_comparesAvailableOnly() {
        KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(50L).connectivityKeysCents(10L).build();

        assertThat(wallet.hasSufficientPurchaseKeysCents(50L)).isTrue();
        assertThat(wallet.hasSufficientPurchaseKeysCents(51L)).isFalse();
        assertThat(wallet.hasSufficientConnectivityKeysCents(10L)).isTrue();
        assertThat(wallet.hasSufficientConnectivityKeysCents(11L)).isFalse();
    }

    @Test
    @DisplayName("onCreate (hook @PrePersist): inicializa todos los contadores en cero si vienen null")
    void onCreate_defaultsNullCountersToZero() {
        KeyWallet wallet = new KeyWallet();

        wallet.onCreate();

        assertThat(wallet.getPurchaseKeysCents()).isZero();
        assertThat(wallet.getBlockedPurchaseKeysCents()).isZero();
        assertThat(wallet.getConnectivityKeysCents()).isZero();
        assertThat(wallet.getBlockedConnectivityKeysCents()).isZero();
        assertThat(wallet.getCreatedAt()).isNotNull();
    }
}
