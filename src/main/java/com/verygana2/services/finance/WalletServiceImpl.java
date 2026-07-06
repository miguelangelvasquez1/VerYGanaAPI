package com.verygana2.services.finance;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.wallet.responses.BillingSummaryResponseDTO;
import com.verygana2.dtos.wallet.responses.BillingSummaryResponseDTO.ActivePlan;
import com.verygana2.dtos.wallet.responses.DepositResponseDTO;
import com.verygana2.dtos.wallet.responses.DepositResponseDTO.DepositType;
import com.verygana2.dtos.wallet.responses.PayoutSummaryResponseDTO;
import com.verygana2.exceptions.financeExceptions.WalletAlreadyExistsException;
import com.verygana2.models.enums.finance.WalletStatus;
import com.verygana2.models.enums.finance.plans.SubscriptionStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Subscription;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.repositories.finance.plans.BudgetTransactionRepository;
import com.verygana2.repositories.finance.plans.InvestmentRepository;
import com.verygana2.repositories.finance.plans.SubscriptionRepository;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.WalletService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final CommercialDetailsService commercialDetailsService;
    private final SubscriptionRepository subscriptionRepository;
    private final InvestmentRepository investmentRepository;
    private final PayoutRepository payoutRepository;
    private final BudgetTransactionRepository budgetTransactionRepository;

    @Override
    public Wallet createFor(Long commercialId) {
        if (walletRepository.existsByCommercialId(commercialId)) {
            throw new WalletAlreadyExistsException("Commercial with id: " + commercialId + " already has a wallet");
        }
        return walletRepository.save(
                Objects.requireNonNull(Wallet.createFor(commercialDetailsService.getCommercialById(commercialId))));
    }

    @Override
    public Wallet getByCommercialId(Long commercialId) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }
        return walletRepository.findByCommercialId(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found for commercial id: " + commercialId));
    }

    @Override
    public BillingSummaryResponseDTO getBillingSummary(Long commercialId) {
        CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime from = now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime to = from.plusMonths(1);

        // Planes BASIC no tienen wallet — los campos dependientes quedan en 0/null
        Wallet wallet = walletRepository.findByCommercialId(commercialId).orElse(null);

        Long balanceCents = wallet != null ? wallet.getBalanceCents() : 0L;
        WalletStatus walletStatus = wallet != null ? wallet.getStatus() : null;
        Long spentThisMonthCents = wallet != null
                ? budgetTransactionRepository.sumByWalletIdAndPeriod(wallet.getId(), from, to).longValue()
                : 0L;

        BigDecimal earned = payoutRepository.sumTotalByCommercialIdAndPeriod(commercialId, from, to);

        return BillingSummaryResponseDTO.builder()
                .balanceCents(balanceCents)
                .walletStatus(walletStatus)
                .spentThisMonthCents(spentThisMonthCents)
                .earnedThisMonthCents(earned != null ? earned.longValue() : 0L)
                .currentPlan(buildActivePlan(commercial, wallet))
                .build();
    }

    @Override
    public PagedResponse<DepositResponseDTO> getDeposits(Long commercialId, int year, int month, Pageable pageable) {
        ZonedDateTime from = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime to = from.plusMonths(1);

        List<DepositResponseDTO> all = new ArrayList<>();

        subscriptionRepository.findByCommercialIdAndPeriod(commercialId, from, to)
                .stream()
                .map(this::mapSubscription)
                .forEach(all::add);

        // Planes BASIC no tienen wallet — solo se consultan investments si existe
        walletRepository.findByCommercialId(commercialId).ifPresent(wallet ->
                investmentRepository.findByWalletIdAndPeriod(wallet.getId(), from, to)
                        .stream()
                        .map(this::mapInvestment)
                        .forEach(all::add));

        all.sort(Comparator.comparing(DepositResponseDTO::getDate).reversed());

        int start = (int) pageable.getOffset();
        if (start >= all.size()) {
            return PagedResponse.from(new PageImpl<>(List.of(), pageable, all.size()));
        }
        int end = Math.min(start + pageable.getPageSize(), all.size());

        return PagedResponse.from(new PageImpl<>(all.subList(start, end), pageable, all.size()));
    }

    @Override
    public PagedResponse<PayoutSummaryResponseDTO> getPayouts(Long commercialId, int year, int month, Pageable pageable) {
        ZonedDateTime from = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime to = from.plusMonths(1);

        return PagedResponse.from(payoutRepository.findByCommercialIdAndPeriod(commercialId, from, to, pageable)
                .map(p -> new PayoutSummaryResponseDTO(
                        p.getId(),
                        p.getGrossAmountCents(),
                        p.getCommissionCents(),
                        p.getNetAmountCents(),
                        p.getStatus(),
                        p.getScheduledAt(),
                        p.getPaidAt())));
    }

    private ActivePlan buildActivePlan(CommercialDetails commercial, Wallet wallet) {
        Plan plan = commercial.getCurrentPlan();
        if (plan == null) return null;

        if (plan.isMonthlySubscription()) {
            // BASIC: acceso temporal — endDate y daysRemaining vienen de la Subscription activa
            return subscriptionRepository
                    .findByCommercialAndStatus(commercial, SubscriptionStatus.ACTIVE)
                    .map(sub -> ActivePlan.builder()
                            .planName(plan.getName())
                            .planCode(plan.getCode())
                            .endDate(sub.getEndDate())
                            .daysRemaining(sub.daysRemaining())
                            .status(sub.getStatus().name())
                            .build())
                    .orElse(ActivePlan.builder()
                            .planName(plan.getName())
                            .planCode(plan.getCode())
                            .endDate(null)
                            .daysRemaining(null)
                            .status(SubscriptionStatus.EXPIRED.name())
                            .build());
        }

        // STANDARD / PREMIUM: acceso por saldo — endDate y daysRemaining no aplican
        return ActivePlan.builder()
                .planName(plan.getName())
                .planCode(plan.getCode())
                .endDate(null)
                .daysRemaining(null)
                .status(wallet.getStatus().name())
                .build();
    }

    private DepositResponseDTO mapSubscription(Subscription sub) {
        return DepositResponseDTO.builder()
                .type(DepositType.SUBSCRIPTION)
                .description("Plan " + sub.getPlan().getName())
                .amountCents(sub.getAmountPaidCents())
                .referenceId(sub.getWompiReference())
                .date(sub.getCreatedAt())
                .status(sub.getStatus().name())
                .build();
    }

    private DepositResponseDTO mapInvestment(Investment inv) {
        return DepositResponseDTO.builder()
                .type(DepositType.INVESTMENT)
                .description("Recarga de presupuesto")
                .amountCents(inv.getDepositAmountCents())
                .referenceId(inv.getWompiReference())
                .date(inv.getCreatedAt())
                .status(Boolean.TRUE.equals(inv.getConfirmed()) ? "CONFIRMED" : "PENDING")
                .build();
    }
}
