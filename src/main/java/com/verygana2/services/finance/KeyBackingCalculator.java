package com.verygana2.services.finance;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.records.KeyBacking;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.branding.BrandingRequestRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.repositories.surveys.SurveyRepository;

import lombok.RequiredArgsConstructor;

/**
 * Arma la identidad contable del fondo de llaves.
 *
 * Vive aparte porque la consumen dos sitios con ritmos distintos —
 * {@code TreasuryServiceImpl.runReconciliation} (semanal, a log) y
 * {@code TreasuryMetrics} (cada minuto, a Prometheus)— y tenerla duplicada era
 * garantía de que un día divergieran: se agrega un flujo nuevo que compromete
 * presupuesto, alguien lo suma en un lado y no en el otro, y la alerta empieza a
 * contradecir al log sin que nadie sepa cuál miente.
 *
 * AL AGREGAR UN FLUJO QUE DESCUENTE DE LA WALLET DEL ANUNCIANTE hay que sumarlo
 * acá, o la identidad va a reportar déficit permanente por dinero que sí está
 * respaldado.
 */
@Component
@RequiredArgsConstructor
public class KeyBackingCalculator {

    private final KeyWalletRepository keyWalletRepository;
    private final WalletRepository walletRepository;
    private final AdRepository adRepository;
    private final SurveyRepository surveyRepository;
    private final BrandingRequestRepository brandingRequestRepository;
    private final CampaignRepository campaignRepository;

    @Transactional(readOnly = true)
    public KeyBacking compute(long keysReserveCents) {
        return new KeyBacking(
                keysReserveCents,
                keyWalletRepository.sumLiveKeyLiabilityCents(),
                walletRepository.sumBalanceCents(),
                adRepository.sumCommittedUnspentBudgetCents(),
                surveyRepository.sumCommittedUnspentBudgetCents(),
                brandingRequestRepository.sumCommittedBudgetCents(),
                campaignRepository.sumUnspentBudgetCents());
    }
}
