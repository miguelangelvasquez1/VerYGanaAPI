package com.verygana2.services.finance;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.keys.SpendKeysRequestDTO;
import com.verygana2.dtos.keys.SpendKeysResponseDTO;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.pets.PetCatalogItem;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.pet.PetCatalogItemRepository;
import com.verygana2.services.finance.KeyWalletServiceImpl.RewardSplit;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link KeyWalletServiceImpl}: el reparto 75/25 entre llaves de
 * compra y conectividad, las fechas de vencimiento calculadas en zona
 * Colombia (con un {@link Clock} fijo para que el test sea determinístico),
 * y el gasto de llaves para la mascota virtual.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyWalletServiceImpl")
class KeyWalletServiceImplTest {

    private static final long KEY_VALUE_CENTS = 1000L;

    @Mock private KeyTransactionRepository keyTransactionRepository;
    @Mock private KeyWalletRepository keyWalletRepository;
    @Mock private ConsumerDetailsService consumerDetailsService;
    @Mock private PetCatalogItemRepository petCatalogItemRepository;

    private KeyWalletServiceImpl service;

    private void setUpWithClock(Clock clock) {
        service = new KeyWalletServiceImpl(clock, keyTransactionRepository, keyWalletRepository,
                consumerDetailsService, petCatalogItemRepository);
        ReflectionTestUtils.setField(service, "PURCHASE_KEYS_PERCENTAGE", 75L);
        ReflectionTestUtils.setField(service, "keyValueCents", KEY_VALUE_CENTS);
    }

    @BeforeEach
    void setUp() {
        setUpWithClock(Clock.systemUTC());
    }

    @Nested
    @DisplayName("calculate (reparto 75/25)")
    class Calculate {

        @Test
        @DisplayName("reparte 75% a compra y el resto a conectividad, sin perder unidades por redondeo")
        void splitsIntoSeventyFiveTwentyFive() {
            RewardSplit split = service.calculate(100L);

            assertThat(split.purchaseKeysReward()).isEqualTo(75L);
            assertThat(split.connectivityKeysReward()).isEqualTo(25L);
            assertThat(split.purchaseKeysReward() + split.connectivityKeysReward()).isEqualTo(100L);
        }

        @Test
        @DisplayName("monto no positivo: retorna reparto en cero para ambas")
        void nonPositiveAmount_returnsZeroSplit() {
            RewardSplit split = service.calculate(0L);

            assertThat(split.purchaseKeysReward()).isZero();
            assertThat(split.connectivityKeysReward()).isZero();
        }

        @Test
        @DisplayName("montos impares también suman exactamente el total (redondeo controlado)")
        void oddAmounts_stillSumExactly() {
            for (long amount : new long[] { 1L, 3L, 7L, 99L, 101L }) {
                RewardSplit split = service.calculate(amount);
                assertThat(split.purchaseKeysReward() + split.connectivityKeysReward()).isEqualTo(amount);
            }
        }
    }

    @Nested
    @DisplayName("calculatePurchaseExpiry / calculateConnectivityExpiry")
    class ExpiryDates {

        @Test
        @DisplayName("purchase: vence el día 1 del mes siguiente a las 00:00 hora Colombia")
        void purchaseExpiry_firstDayOfNextMonth() {
            // 15 de abril 2026, 10am UTC = 5am Colombia
            Instant fixedInstant = ZonedDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
            setUpWithClock(Clock.fixed(fixedInstant, ZoneOffset.UTC));

            ZonedDateTime expiry = service.calculatePurchaseExpiry();
            ZonedDateTime expiryColombia = expiry.withZoneSameInstant(java.time.ZoneId.of("America/Bogota"));

            assertThat(expiryColombia.getMonthValue()).isEqualTo(5);
            assertThat(expiryColombia.getDayOfMonth()).isEqualTo(1);
            assertThat(expiryColombia.getHour()).isZero();
        }

        @Test
        @DisplayName("connectivity: vence exactamente 24 horas después de ahora (misma hora, día siguiente), no al inicio del día")
        void connectivityExpiry_exactlyOneDayLater() {
            Instant fixedInstant = ZonedDateTime.of(2026, 4, 8, 23, 0, 0, 0, ZoneOffset.UTC).toInstant();
            setUpWithClock(Clock.fixed(fixedInstant, ZoneOffset.UTC));

            ZonedDateTime expiry = service.calculateConnectivityExpiry();
            ZonedDateTime expiryColombia = expiry.withZoneSameInstant(java.time.ZoneId.of("America/Bogota"));
            ZonedDateTime nowColombia = ZonedDateTime.now(Clock.fixed(fixedInstant, ZoneOffset.UTC))
                    .withZoneSameInstant(java.time.ZoneId.of("America/Bogota"));

            assertThat(expiryColombia.toLocalDate()).isEqualTo(nowColombia.toLocalDate().plusDays(1));
            assertThat(expiryColombia.getHour()).isEqualTo(nowColombia.getHour());
        }
    }

    @Test
    @DisplayName("getByConsumerId: id inválido lanza IllegalArgumentException; inexistente lanza EntityNotFoundException")
    void getByConsumerId_validations() {
        assertThatThrownBy(() -> service.getByConsumerId(0L)).isInstanceOf(IllegalArgumentException.class);

        when(keyWalletRepository.findByConsumerId(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getByConsumerId(99L)).isInstanceOf(EntityNotFoundException.class);
    }

    /** Prenda de la tienda de ropa: sin externalId, se identifica por nombre. */
    private static PetCatalogItem monoculo() {
        PetCatalogItem item = new PetCatalogItem();
        item.setId(40L);
        item.setName("monoculo");
        item.setPrice(12);
        item.setActive(false);
        return item;
    }

    /** Ítem del catálogo: PK interna 5, externalId 1005 (lo que ve el juego), 25 llaves. */
    private static PetCatalogItem pizza() {
        PetCatalogItem item = new PetCatalogItem();
        item.setId(5L);
        item.setExternalId(1005);
        item.setName("Pizza");
        item.setPrice(25);
        item.setActive(true);
        return item;
    }

    @Nested
    @DisplayName("spendKeysForPetGame")
    class SpendKeysForPetGame {

        @Test
        @DisplayName("saldo suficiente: gasta las llaves y retorna el nuevo balance")
        void sufficientBalance_spendsKeys() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));

            SpendKeysResponseDTO response = service.spendKeysForPetGame(9L,
                    new SpendKeysRequestDTO(30L * KEY_VALUE_CENTS, 1, "Sombrero"));

            assertThat(response.success()).isTrue();
            assertThat(response.newBalance()).isEqualTo(70L);
            verify(keyTransactionRepository).save(any());
        }

        @Test
        @DisplayName("saldo insuficiente: retorna fallo sin gastar ni registrar transacción")
        void insufficientBalance_returnsFailureWithoutSpending() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(10L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));

            SpendKeysResponseDTO response = service.spendKeysForPetGame(9L,
                    new SpendKeysRequestDTO(30L * KEY_VALUE_CENTS, 1, "Sombrero"));

            assertThat(response.success()).isFalse();
            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(10L * KEY_VALUE_CENTS); // sin cambios
            verify(keyTransactionRepository, org.mockito.Mockito.never()).save(any());
        }

        /**
         * El precio lo pone el servidor: el juego solo dice QUÉ compró y cuántas
         * unidades. Sin esto, mandar amount=1 alcanzaba para llevarse un ítem de
         * 25 llaves por 1.
         *
         * El id llega en itemName porque el build deja itemId en 0 (ver
         * SpendKeysRequestDTO); esta prueba usa el payload real del juego.
         */
        @Test
        @DisplayName("cobra el precio del catálogo, no el monto que manda el cliente")
        void chargesCatalogPrice_notClientAmount() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));
            when(petCatalogItemRepository.findByExternalId(1005)).thenReturn(Optional.of(pizza()));

            SpendKeysResponseDTO response = service.spendKeysForPetGame(9L,
                    new SpendKeysRequestDTO(null, 1L, 0, "1005"));   // Pizza: externalId=1005, price=25

            assertThat(response.success()).isTrue();
            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(75L * KEY_VALUE_CENTS);
        }

        @Test
        @DisplayName("multiplica el precio del catálogo por la cantidad")
        void multipliesCatalogPriceByQuantity() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));
            when(petCatalogItemRepository.findByExternalId(1005)).thenReturn(Optional.of(pizza()));

            service.spendKeysForPetGame(9L, new SpendKeysRequestDTO(null, 3L, 0, "1005"));

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(25L * KEY_VALUE_CENTS); // 100 - 3×25
        }

        /**
         * Los ítems horneados en el build no están en pet_catalog_items, y la ropa
         * ni siquiera manda un id numérico ("monoculo"). Esos siguen cobrando el
         * monto del cliente hasta que el catálogo del juego venga solo de la API.
         */
        @Test
        @DisplayName("ítem que no está en el catálogo: cae al monto del cliente")
        void unknownItem_fallsBackToClientAmount() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));

            service.spendKeysForPetGame(9L, new SpendKeysRequestDTO(null, 1L, 0, "monoculo"));

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(99L * KEY_VALUE_CENTS);
        }

        /**
         * Sin este id, medir ventas por producto obligaba a partir el texto de `reason`
         * ("Mascota virtual: 1005 (itemId=0)"): si alguien cambiaba ese formato, los
         * informes devolvían cero sin fallar.
         */
        @Test
        @DisplayName("la transacción guarda el id del ítem del catálogo")
        void transactionKeepsCatalogItemId() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));
            when(petCatalogItemRepository.findByExternalId(1005)).thenReturn(Optional.of(pizza()));

            service.spendKeysForPetGame(9L, new SpendKeysRequestDTO(null, 1L, 0, "1005"));

            org.mockito.ArgumentCaptor<KeyTransaction> captor =
                    org.mockito.ArgumentCaptor.forClass(KeyTransaction.class);
            verify(keyTransactionRepository).save(captor.capture());
            assertThat(captor.getValue().getPetCatalogItemId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("un ítem sin registrar deja el id nulo, no inventa uno")
        void unknownItem_leavesCatalogItemIdNull() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));

            service.spendKeysForPetGame(9L, new SpendKeysRequestDTO(null, 1L, 0, "no_existe"));

            org.mockito.ArgumentCaptor<KeyTransaction> captor =
                    org.mockito.ArgumentCaptor.forClass(KeyTransaction.class);
            verify(keyTransactionRepository).save(captor.capture());
            assertThat(captor.getValue().getPetCatalogItemId()).isNull();
        }

        /**
         * La tienda de ropa manda el nombre interno de la prenda en vez de un id
         * numérico, así que resolveCatalogId() devuelve null. Buscando por nombre se
         * cobra igualmente el precio del servidor.
         */
        @Test
        @DisplayName("ropa: cobra el precio del catálogo buscando por nombre")
        void clothing_chargesCatalogPriceByName() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));
            when(petCatalogItemRepository.findByNameIgnoreCase("monoculo"))
                    .thenReturn(Optional.of(monoculo()));

            // El cliente dice 1 llave; el catálogo dice 12.
            service.spendKeysForPetGame(9L, new SpendKeysRequestDTO(null, 1L, 0, "monoculo"));

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(88L * KEY_VALUE_CENTS);
        }

        @Test
        @DisplayName("ropa: multiplica por la cantidad")
        void clothing_multipliesByQuantity() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));
            when(petCatalogItemRepository.findByNameIgnoreCase("monoculo"))
                    .thenReturn(Optional.of(monoculo()));

            service.spendKeysForPetGame(9L, new SpendKeysRequestDTO(null, 2L, 0, "monoculo"));

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(76L * KEY_VALUE_CENTS);
        }

        @Test
        @DisplayName("ropa: el nombre se recorta antes de buscarlo")
        void clothing_trimsName() {
            // El build manda algún nombre con espacios sobrantes (el objeto de escena
            // 'camita ' ya venía así); sin recortar, la búsqueda falla y se vuelve a
            // cobrar el precio del cliente sin que nadie lo note.
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));
            when(petCatalogItemRepository.findByNameIgnoreCase("monoculo"))
                    .thenReturn(Optional.of(monoculo()));

            service.spendKeysForPetGame(9L, new SpendKeysRequestDTO(null, 1L, 0, "  monoculo  "));

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(88L * KEY_VALUE_CENTS);
        }

        @Test
        @DisplayName("un id numérico sigue resolviéndose por externalId, no por nombre")
        void numericName_stillUsesExternalId() {
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(100L * KEY_VALUE_CENTS).build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));
            when(petCatalogItemRepository.findByExternalId(1005)).thenReturn(Optional.of(pizza()));

            service.spendKeysForPetGame(9L, new SpendKeysRequestDTO(null, 1L, 0, "1005"));

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(75L * KEY_VALUE_CENTS);
            verify(petCatalogItemRepository, org.mockito.Mockito.never())
                    .findByNameIgnoreCase(org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Nested
    @DisplayName("getBalance")
    class GetBalance {

        /**
         * Regresión: getBalance sumaba conectividad pero spendKeysForPetGame solo
         * puede debitar llaves de compra, así que el juego mostraba saldo gastable
         * que al usarse daba "saldo insuficiente".
         */
        @Test
        @DisplayName("solo cuenta llaves de compra, no las de conectividad")
        void countsOnlyPurchaseKeys() {
            KeyWallet wallet = KeyWallet.builder()
                    .purchaseKeysCents(401L)        // 0,401 llaves
                    .connectivityKeysCents(799L)    // 0,799 llaves
                    .build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));

            assertThat(service.getBalance(9L).balance()).isZero();
        }
    }
}
