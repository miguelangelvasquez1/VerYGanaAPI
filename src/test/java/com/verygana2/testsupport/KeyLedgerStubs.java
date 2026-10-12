package com.verygana2.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import java.util.UUID;

import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.services.interfaces.finance.KeyLedgerService;
import com.verygana2.services.interfaces.finance.KeyLedgerService.Issuance;

/**
 * Hace que un mock de {@link KeyLedgerService} mueva la billetera igual que el real,
 * para los tests unitarios de quienes lo llaman: así pueden seguir afirmando sobre el
 * saldo resultante sin montar lotes ni repositorios. El libro y los lotes en sí se
 * prueban en KeyLedgerServiceIntegrationTest.
 */
public final class KeyLedgerStubs {

    private KeyLedgerStubs() {
    }

    // Todo con doAnswer(..).when(mock): la forma when(mock.metodo(any())) ejecuta la
    // respuesta ya registrada con argumentos nulos si un test vuelve a llamar aquí.
    public static void applyToWallet(KeyLedgerService ledger) {
        lenient().doAnswer(inv -> {
            KeyWallet wallet = inv.getArgument(0);
            Issuance issuance = inv.getArgument(1);
            wallet.creditKeysCents(issuance.purchaseCents(), issuance.connectivityCents());
            return null;
        }).when(ledger).issue(any(), any());

        lenient().doAnswer(inv -> inv.<KeyWallet>getArgument(0).getPurchaseKeysCents())
                .when(ledger).spendablePurchaseCents(any());

        lenient().doAnswer(inv -> {
            inv.<KeyWallet>getArgument(0).reservePurchaseKeysCents(inv.getArgument(1));
            return entry();
        }).when(ledger).reserveForCopayment(any(), anyLong(), any());

        lenient().doAnswer(inv -> {
            inv.<KeyWallet>getArgument(0).confirmReservedPurchaseKeysCents(inv.getArgument(1));
            return entry();
        }).when(ledger).confirmCopayment(any(), anyLong(), any(), any());

        lenient().doAnswer(inv -> {
            inv.<KeyWallet>getArgument(0).releasePurchaseKeysCents(inv.getArgument(1));
            return entry();
        }).when(ledger).releaseCopayment(any(), anyLong(), any());

        lenient().doAnswer(inv -> {
            inv.<KeyWallet>getArgument(0).creditKeysCents(inv.getArgument(1), 0);
            return entry();
        }).when(ledger).refundPurchaseItem(any(), anyLong(), any(), any(), any());

        lenient().doAnswer(inv -> {
            inv.<KeyWallet>getArgument(0).spendPurchaseKeysCents(inv.getArgument(1));
            return entry();
        }).when(ledger).spendForPetGame(any(), anyLong(), any(), any(), any());
    }

    private static KeyTransaction entry() {
        return KeyTransaction.builder().id(UUID.randomUUID()).build();
    }
}
