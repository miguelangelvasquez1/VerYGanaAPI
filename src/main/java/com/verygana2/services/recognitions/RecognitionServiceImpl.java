package com.verygana2.services.recognitions;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.verygana2.dtos.recognitions.ConsumerRecognitionDTO;
import com.verygana2.dtos.recognitions.CreateRecognitionRequestDTO;
import com.verygana2.dtos.recognitions.RecognitionCandidateDTO;
import com.verygana2.dtos.recognitions.RecognitionDetailDTO;
import com.verygana2.dtos.recognitions.RecognitionFundDTO;
import com.verygana2.dtos.recognitions.RecognitionSummaryDTO;
import com.verygana2.event.PurchaseItemClaimedEvent;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.enums.AccountStatus;
import com.verygana2.models.enums.marketplace.ComputableSaleStatus;
import com.verygana2.models.enums.marketplace.PurchaseBuyerType;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.recognitions.RecognitionCriterion;
import com.verygana2.models.enums.recognitions.RecognitionStatus;
import com.verygana2.models.levels.UserLevelProfile;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.recognitions.Recognition;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.repositories.finance.PayoutItemRepository;
import com.verygana2.repositories.finance.TreasuryMovementRepository;
import com.verygana2.repositories.levels.UserLevelProfileRepository;
import com.verygana2.repositories.marketplace.PurchaseRepository;
import com.verygana2.repositories.marketplace.SaleComponentRepository;
import com.verygana2.repositories.recognitions.RecognitionCandidateRepository;
import com.verygana2.repositories.recognitions.RecognitionCandidateRepository.CandidateRow;
import com.verygana2.repositories.recognitions.RecognitionRepository;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.storage.service.PublicUrlBuilder;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.finance.ProsperityService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.recognitions.RecognitionService;
import com.verygana2.services.marketplace.PurchaseDeliverer;
import com.verygana2.services.marketplace.PurchaseItemAssembler;
import com.verygana2.services.marketplace.SaleComponentRecorder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RecognitionServiceImpl implements RecognitionService {

    static final String TREASURY_REFERENCE_TYPE = "INSTITUTIONAL_PURCHASE";
    private static final Duration MAX_CANDIDATE_PERIOD = Duration.ofDays(366);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 120;
    private static final Set<PurchaseItemStatus> CANCELLABLE_ITEM_STATUSES = Set.of(
            PurchaseItemStatus.PENDING, PurchaseItemStatus.CLAIMED, PurchaseItemStatus.IN_REVIEW);

    private final RecognitionRepository recognitionRepository;
    private final RecognitionCandidateRepository candidateRepository;
    private final PurchaseRepository purchaseRepository;
    private final SaleComponentRepository saleComponentRepository;
    private final PayoutItemRepository payoutItemRepository;
    private final TreasuryMovementRepository treasuryMovementRepository;
    private final ConsumerDetailsRepository consumerDetailsRepository;
    private final UserLevelProfileRepository userLevelProfileRepository;
    private final ConsumerDetailsService consumerDetailsService;
    private final ProductService productService;
    private final PurchaseItemAssembler purchaseItemAssembler;
    private final PurchaseDeliverer purchaseDeliverer;
    private final SaleComponentRecorder saleComponentRecorder;
    private final ProsperityService prosperityService;
    private final TreasuryService treasuryService;
    private final InstitutionalPurchaseReverser reverser;
    private final EmailService emailService;
    private final UserIdResolver userIdResolver;
    private final ProductCodeEncryptor productCodeEncryptor;
    private final PublicUrlBuilder publicUrlBuilder;
    private final long keyValueCents;

    public RecognitionServiceImpl(RecognitionRepository recognitionRepository,
            RecognitionCandidateRepository candidateRepository, PurchaseRepository purchaseRepository,
            SaleComponentRepository saleComponentRepository, PayoutItemRepository payoutItemRepository,
            TreasuryMovementRepository treasuryMovementRepository, ConsumerDetailsRepository consumerDetailsRepository,
            UserLevelProfileRepository userLevelProfileRepository, ConsumerDetailsService consumerDetailsService,
            ProductService productService, PurchaseItemAssembler purchaseItemAssembler,
            PurchaseDeliverer purchaseDeliverer, SaleComponentRecorder saleComponentRecorder,
            ProsperityService prosperityService, TreasuryService treasuryService,
            InstitutionalPurchaseReverser reverser, EmailService emailService, UserIdResolver userIdResolver,
            ProductCodeEncryptor productCodeEncryptor, PublicUrlBuilder publicUrlBuilder,
            @Value("${financial.key-value-cents}") long keyValueCents) {
        this.recognitionRepository = recognitionRepository;
        this.candidateRepository = candidateRepository;
        this.purchaseRepository = purchaseRepository;
        this.saleComponentRepository = saleComponentRepository;
        this.payoutItemRepository = payoutItemRepository;
        this.treasuryMovementRepository = treasuryMovementRepository;
        this.consumerDetailsRepository = consumerDetailsRepository;
        this.userLevelProfileRepository = userLevelProfileRepository;
        this.consumerDetailsService = consumerDetailsService;
        this.productService = productService;
        this.purchaseItemAssembler = purchaseItemAssembler;
        this.purchaseDeliverer = purchaseDeliverer;
        this.saleComponentRecorder = saleComponentRecorder;
        this.prosperityService = prosperityService;
        this.treasuryService = treasuryService;
        this.reverser = reverser;
        this.emailService = emailService;
        this.userIdResolver = userIdResolver;
        this.productCodeEncryptor = productCodeEncryptor;
        this.publicUrlBuilder = publicUrlBuilder;
        this.keyValueCents = keyValueCents;
    }

    // ─── Compra ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public RecognitionDetailDTO create(CreateRecognitionRequestDTO request, Long adminId, String idempotencyKey) {
        String originKey = normalizeOriginKey(idempotencyKey);

        // Reintento: la compra ya existe, se devuelve en vez de crear otra.
        Optional<Recognition> existing = recognitionRepository.findByOriginKey(originKey);
        if (existing.isPresent()) {
            log.info("[RECOGNITION] Reintento con la misma clave de origen, se devuelve {}", existing.get().getPublicId());
            return toDetail(existing.get());
        }

        validatePeriod(request.periodFrom(), request.periodTo(), false);
        ConsumerDetails beneficiary = resolveBeneficiary(request.beneficiaryPublicId());

        // Aviso temprano sin bloquear: la decisión real la toma tesorería con la cuenta
        // bloqueada al final. Evita reservar stock para una compra que no se puede pagar.
        Product product = productService.getById(request.productId());
        long expectedTotal = product.getPriceCents() * request.quantity();
        if (treasuryService.fortificationBalanceCents() < expectedTotal) {
            throw new BusinessException("El fondo de fortalecimiento no cubre el precio completo del reconocimiento");
        }

        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .referenceId(UUID.randomUUID().toString())
                .buyerType(PurchaseBuyerType.VERYGANA)
                .beneficiary(beneficiary)
                .originKey(originKey)
                .deliveryEmail(beneficiary.getUser().getEmail())
                .deliveryEmailVerified(true)
                .build());

        purchaseItemAssembler.addItems(purchase, product.getId(), request.quantity());
        purchase.calculateFinancials();
        // Los ítems necesitan id: lo usan los componentes de venta y el libro de Prosperidad.
        purchaseRepository.flush();

        UUID fundingRef = UUID.randomUUID();

        // Venta Computable: el pago de VERYGANA cubre el precio completo de cada ítem.
        saleComponentRecorder.recognizeInstitutionalSale(purchase, fundingRef);
        // Régimen ordinario sin cambios por ser un reconocimiento: A = min(V, S0), B = V - A, C = B × t.
        prosperityService.absorbPurchase(purchase);
        purchaseDeliverer.deliver(purchase);
        purchase.markAsCompleted();

        Recognition recognition = recognitionRepository.save(Recognition.builder()
                .purchase(purchase)
                .fundingRef(fundingRef)
                .selectionCriterion(request.criterion())
                .selectionMetricValue(request.metricValue())
                .selectionPeriodFrom(request.periodFrom())
                .selectionPeriodTo(request.periodTo())
                .reason(request.reason().trim())
                .createdByAdminId(adminId)
                .build());
        // Un producto digital queda entregado en este mismo paso; uno físico espera el PIN.
        if (purchase.getItems().stream().allMatch(i -> i.getStatus() == PurchaseItemStatus.CLAIMED)) {
            recognition.markDelivered();
        }

        // Tesorería al final: sus cuentas son filas globales bloqueadas hasta el commit, y
        // el orden prosperidad → tesorería es el de todos los flujos de venta.
        treasuryService.fundInstitutionalPurchase(purchase.getTotalCents(), fundingRef);
        if (purchase.getCommissionCents() > 0) {
            treasuryService.retainCommission(purchase.getCommissionCents(), purchase.getCommissionVatCents(),
                    fundingRef, TREASURY_REFERENCE_TYPE);
        }
        purchaseRepository.save(purchase);

        String recognitionRef = recognition.getPublicId().toString();
        afterCommit(() -> {
            emailService.sendRecognitionDelivery(purchase, recognitionRef);
            emailService.sendCommercialSaleNotification(purchase);
        });

        log.info("[RECOGNITION] Reconocimiento creado: id={}, purchaseId={}, total={}, absorbido={}, comision={}, admin={}",
                recognition.getPublicId(), purchase.getId(), purchase.getTotalCents(),
                purchase.getProsperityAbsorbedCents(), purchase.getCommissionCents(), adminId);
        return toDetail(recognition);
    }

    /** El comercio validó el PIN de un ítem: si era de un reconocimiento, puede quedar entregado. */
    @EventListener
    public void onItemClaimed(PurchaseItemClaimedEvent event) {
        recognitionRepository.findByPurchaseId(event.getPurchaseId())
                .filter(r -> r.getStatus() == RecognitionStatus.PURCHASED)
                .ifPresent(reverser::settleStatus);
    }

    @Override
    @Transactional
    public RecognitionDetailDTO cancel(UUID publicId, String cause, Long adminId) {
        Recognition recognition = recognitionRepository.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> notFound(publicId));
        if (recognition.getStatus() == RecognitionStatus.CANCELLED
                || recognition.getStatus() == RecognitionStatus.EXPIRED) {
            throw new InvalidStatusException("El reconocimiento ya está cerrado: " + recognition.getStatus());
        }

        List<PurchaseItem> items = recognition.getPurchase().getItems();
        // Todo o nada: si un ítem ya se le pagó al comercio, no se anula ninguno.
        for (PurchaseItem item : items) {
            if (payoutItemRepository.existsByPurchaseItemId(item.getId())) {
                throw new InvalidStatusException(
                        "No se puede anular: el ítem " + item.getId() + " ya fue pagado al comercio");
            }
        }

        String trimmedCause = cause.trim();
        for (PurchaseItem item : items) {
            reverser.reverse(item, "CANCELLED:" + trimmedCause);
            // El código ya se le entregó al beneficiario: el stock no vuelve al inventario.
            if (CANCELLABLE_ITEM_STATUSES.contains(item.getStatus())) {
                item.setStatus(PurchaseItemStatus.REFUNDED);
            }
        }
        recognition.cancel(trimmedCause, adminId);

        log.info("[RECOGNITION] Reconocimiento anulado: id={}, admin={}, causa={}", publicId, adminId, trimmedCause);
        return toDetail(recognition);
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public RecognitionDetailDTO get(UUID publicId) {
        return toDetail(recognitionRepository.findByPublicId(publicId).orElseThrow(() -> notFound(publicId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RecognitionSummaryDTO> search(RecognitionStatus status, UUID beneficiaryPublicId,
            UUID commercialPublicId, ZonedDateTime from, ZonedDateTime to, Pageable pageable) {
        Long beneficiaryId = beneficiaryPublicId == null ? null : userIdResolver.toInternalId(beneficiaryPublicId);
        Long commercialId = commercialPublicId == null ? null : userIdResolver.toInternalId(commercialPublicId);
        return recognitionRepository.search(status, beneficiaryId, commercialId, from, to, pageable)
                .map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RecognitionCandidateDTO> candidates(RecognitionCriterion criterion, ZonedDateTime from,
            ZonedDateTime to, Pageable pageable) {
        Page<CandidateRow> rows = switch (criterion) {
            case XP -> candidateRepository.rankByXp(pageable);
            case OTHER -> throw new IllegalArgumentException("OTHER no es un criterio de ranking");
            default -> {
                validatePeriod(from, to, true);
                yield switch (criterion) {
                    case KEYS_EARNED -> candidateRepository.rankByKeysEarned(from, to, pageable);
                    case PURCHASES_COUNT -> candidateRepository.rankByPurchasesCount(from, to, pageable);
                    case PURCHASES_AMOUNT -> candidateRepository.rankByPurchasesAmount(from, to, pageable);
                    case GAMES_PLAYED -> candidateRepository.rankByGamesPlayed(from, to, pageable);
                    default -> throw new IllegalStateException("Criterio sin ranking: " + criterion);
                };
            }
        };

        List<Long> ids = rows.getContent().stream().map(CandidateRow::getConsumerId).toList();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, rows.getTotalElements());
        }

        Map<Long, ConsumerDetails> consumers = consumerDetailsRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ConsumerDetails::getId, Function.identity()));
        Map<Long, UserLevelProfile> levels = userLevelProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserLevelProfile::getId, Function.identity()));
        Map<Long, Object[]> received = new HashMap<>();
        for (Object[] row : recognitionRepository.countActiveByBeneficiary(ids)) {
            received.put((Long) row[0], row);
        }

        return rows.map(row -> {
            ConsumerDetails consumer = consumers.get(row.getConsumerId());
            UserLevelProfile level = levels.get(row.getConsumerId());
            Object[] rec = received.get(row.getConsumerId());
            long metric = row.getMetric() == null ? 0L : row.getMetric();
            if (criterion == RecognitionCriterion.KEYS_EARNED) {
                metric = metric / keyValueCents;
            }
            return new RecognitionCandidateDTO(
                    consumer.getUser().getPublicId(),
                    fullName(consumer),
                    metric,
                    level == null ? null : level.getCurrentLevel(),
                    level == null ? null : level.getLastActivityAt(),
                    rec == null ? 0L : (Long) rec[1],
                    rec == null ? null : (ZonedDateTime) rec[2]);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public RecognitionFundDTO fund() {
        return new RecognitionFundDTO(
                treasuryService.fortificationBalanceCents(),
                recognitionRepository.sumActiveSpentCents(),
                recognitionRepository.countByStatus(RecognitionStatus.PURCHASED),
                recognitionRepository.countByStatus(RecognitionStatus.DELIVERED),
                recognitionRepository.countByStatus(RecognitionStatus.EXPIRED),
                recognitionRepository.countByStatus(RecognitionStatus.CANCELLED));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConsumerRecognitionDTO> receivedBy(Long consumerId, Pageable pageable) {
        return recognitionRepository.findByBeneficiaryId(consumerId, pageable).map(this::toConsumerView);
    }

    @Override
    @Transactional(readOnly = true)
    public int reconcileFunding() {
        Map<String, Long> fromTreasury = new HashMap<>();
        for (Object[] row : treasuryMovementRepository.sumNetFortificationPurchasesByReference()) {
            fromTreasury.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        Map<String, Long> fromComponents = new HashMap<>();
        for (Object[] row : saleComponentRepository.sumNetVeryganaPaymentsBySourceRef()) {
            fromComponents.put((String) row[0], ((Number) row[1]).longValue());
        }

        Set<String> refs = new java.util.HashSet<>(fromTreasury.keySet());
        refs.addAll(fromComponents.keySet());
        int mismatches = 0;
        for (String ref : refs) {
            long treasury = fromTreasury.getOrDefault(ref, 0L);
            long components = fromComponents.getOrDefault(ref, 0L);
            if (treasury != components) {
                mismatches++;
                log.error("[RECONCILIATION] Recurso del fondo descuadrado: fundingRef={}, tesoreria={}, componentes={}",
                        ref, treasury, components);
            }
        }
        log.info("[RECONCILIATION] Reconocimientos: {} recurso(s) revisado(s), {} descuadre(s)", refs.size(), mismatches);
        return mismatches;
    }

    // ─── Privados ─────────────────────────────────────────────────────────────

    private String normalizeOriginKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("El header Idempotency-Key es obligatorio");
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key no puede superar " + MAX_IDEMPOTENCY_KEY_LENGTH + " caracteres");
        }
        return "RECOGNITION:" + key;
    }

    private void validatePeriod(ZonedDateTime from, ZonedDateTime to, boolean required) {
        if (from == null && to == null && !required) {
            return;
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("El periodo necesita fecha inicial y final");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("La fecha inicial debe ser anterior a la final");
        }
        if (Duration.between(from, to).compareTo(MAX_CANDIDATE_PERIOD) > 0) {
            throw new IllegalArgumentException("El periodo no puede superar 366 días");
        }
    }

    private ConsumerDetails resolveBeneficiary(UUID publicId) {
        ConsumerDetails beneficiary = consumerDetailsService.getConsumerById(userIdResolver.toInternalId(publicId));
        if (beneficiary.getUser().getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("El beneficiario debe tener la cuenta activa");
        }
        return beneficiary;
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("[RECOGNITION] Error en notificaciones posteriores a la compra: {}", e.getMessage(), e);
                }
            }
        });
    }

    private static jakarta.persistence.EntityNotFoundException notFound(UUID publicId) {
        return new jakarta.persistence.EntityNotFoundException("Reconocimiento no encontrado: " + publicId);
    }

    private static String fullName(ConsumerDetails consumer) {
        return (consumer.getName() + " " + consumer.getLastName()).trim();
    }

    private static PurchaseItem firstItem(Purchase purchase) {
        return purchase.getItems().get(0);
    }

    private static String productName(PurchaseItem item) {
        return item.getProduct() != null ? item.getProduct().getName() : item.getProductNameSnapshot();
    }

    private static String commercialName(PurchaseItem item) {
        return item.getProduct() != null ? item.getProduct().getCommercial().getCompanyName() : null;
    }

    private RecognitionSummaryDTO toSummary(Recognition r) {
        Purchase purchase = r.getPurchase();
        PurchaseItem first = firstItem(purchase);
        ConsumerDetails beneficiary = purchase.getBeneficiary();
        return new RecognitionSummaryDTO(
                r.getPublicId(),
                r.getStatus(),
                beneficiary.getUser().getPublicId(),
                fullName(beneficiary),
                productName(first),
                commercialName(first),
                purchase.getItems().size(),
                purchase.getTotalCents(),
                r.getCreatedAt());
    }

    private RecognitionDetailDTO toDetail(Recognition r) {
        Purchase purchase = r.getPurchase();
        PurchaseItem first = firstItem(purchase);
        ConsumerDetails beneficiary = purchase.getBeneficiary();

        List<RecognitionDetailDTO.Item> items = purchase.getItems().stream().map(item -> new RecognitionDetailDTO.Item(
                item.getId(),
                productName(item),
                item.getStatus(),
                item.getComputableStatus(),
                item.getRecognizedAt(),
                item.getSubtotalCents(),
                item.getProsperityAbsorbedCents(),
                item.getCommissionBaseCents(),
                item.getCommissionPctApplied(),
                item.getCommissionCents(),
                item.getCommissionVatCents(),
                item.getCommissionNetOfVatCents(),
                item.getNetToCommercialCents(),
                item.getDeliveredAt(),
                item.getClaimedAt(),
                item.getClaimExpiresAt(),
                item.getId() != null && payoutItemRepository.existsByPurchaseItemId(item.getId()),
                item.getId() == null ? List.of()
                        : saleComponentRepository.findByPurchaseItemIdOrderByIdAsc(item.getId()).stream()
                                .map(c -> new RecognitionDetailDTO.Component(c.getComponentType(), c.getPayerType(),
                                        c.getSourceRef(), c.getAmountCents(), c.getStatus(), c.getCause(),
                                        c.getCreatedAt()))
                                .toList()))
                .toList();

        return new RecognitionDetailDTO(
                r.getPublicId(),
                r.getStatus(),
                r.getSelectionCriterion(),
                r.getSelectionMetricValue(),
                r.getSelectionPeriodFrom(),
                r.getSelectionPeriodTo(),
                r.getReason(),
                userIdResolver.toPublicId(r.getCreatedByAdminId()),
                r.getCreatedAt(),
                r.getDeliveredAt(),
                r.getClosedAt(),
                r.getCancelCause(),
                PurchaseBuyerType.VERYGANA.name(),
                beneficiary.getUser().getPublicId(),
                fullName(beneficiary),
                first.getCommercialId() == null ? null : userIdResolver.toPublicId(first.getCommercialId()),
                commercialName(first),
                purchase.getId(),
                r.getFundingRef(),
                purchase.getTotalCents(),
                purchase.getProsperityAbsorbedCents(),
                purchase.getCommissionCents(),
                purchase.getNetToCommercialsCents(),
                items);
    }

    private ConsumerRecognitionDTO toConsumerView(Recognition r) {
        Purchase purchase = r.getPurchase();
        PurchaseItem first = firstItem(purchase);
        List<ConsumerRecognitionDTO.Item> items = purchase.getItems().stream().map(item -> {
            boolean live = item.getComputableStatus() != ComputableSaleStatus.REVERSED;
            String code = live && item.getDeliveredCode() != null
                    ? productCodeEncryptor.decrypt(item.getDeliveredCode())
                    : null;
            return new ConsumerRecognitionDTO.Item(item.getId(), item.getStatus(), code, item.getClaimExpiresAt());
        }).toList();

        return new ConsumerRecognitionDTO(
                r.getPublicId(),
                r.getStatus(),
                r.getReason(),
                productName(first),
                first.getProduct() != null && first.getProduct().getImageAsset() != null
                        ? publicUrlBuilder.toPublicUrl(first.getProduct().getImageAsset().getObjectKey()) : null,
                commercialName(first),
                r.getCreatedAt(),
                r.getDeliveredAt(),
                items);
    }
}
