package com.verygana2.services.raffles;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.ConfirmRaffleCreationRequestDTO;
import com.verygana2.dtos.raffle.requests.CreatePrizeRequestDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRuleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.ParticipantLeaderboardDTO;
import com.verygana2.dtos.raffle.responses.PrepareRaffleCreationResponseDTO;
import com.verygana2.dtos.raffle.responses.PrizeResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResponseDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.mappers.raffles.PrizeMapper;
import com.verygana2.mappers.raffles.RaffleMapper;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.raffles.RaffleImagePolicy;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.PrizeImageAsset;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleImageAsset;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.repositories.raffles.PrizeImageAssetRepository;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.repositories.raffles.RaffleImageAssetRepository;
import com.verygana2.repositories.raffles.RaffleParticipationRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.TicketEarningRuleRepository;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.storage.service.R2Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RaffleServiceImpl implements RaffleService {

    private final RaffleRepository raffleRepository;
    private final PrizeRepository prizeRepository;
    private final TicketEarningRuleRepository ticketEarningRuleRepository;
    private final RaffleTicketRepository raffleTicketRepository;
    private final RaffleParticipationRepository raffleParticipationRepository;
    private final RaffleImageAssetRepository raffleImageAssetRepository;
    private final PrizeImageAssetRepository prizeImageAssetRepository;
    private final R2Service r2Service;
    private final RaffleMapper raffleMapper;
    private final PrizeMapper prizeMapper;

    private static final String domain = "https://cdn.verygana.com/";

    @Override
    public PrepareRaffleCreationResponseDTO prepareRaffleCreation(Long adminId, CreateRaffleRequestDTO raffleData,
            FileUploadRequestDTO raffleImageMetadata, List<FileUploadRequestDTO> prizeImageMetadataList) {

        log.info("📋 Preparing raffle creation for admin: {}", adminId);

        // Validaciones de negocio antes de tocar storage
        validateDates(raffleData);
        validatePrizes(raffleData.getPrizes());
        validateRules(raffleData);

        // Validar que llegó exactamente una imagen por prize
        if (prizeImageMetadataList.size() != raffleData.getPrizes().size()) {
            throw new InvalidRequestException(
                    String.format("Expected %d prize images, got %d",
                            raffleData.getPrizes().size(),
                            prizeImageMetadataList.size()));
        }

        List<Long> createdAssetIds = new ArrayList<>();

        try {
            // --- Raffle image asset ---
            String raffleObjectKey = generateRaffleObjectKey(raffleImageMetadata);

            RaffleImageAsset raffleAsset = RaffleImageAsset.builder()
                    .objectKey(raffleObjectKey)
                    .sizeBytes(raffleImageMetadata.getSizeBytes())
                    .status(AssetStatus.PENDING)
                    .build();

            RaffleImageAsset savedRaffleAsset = raffleImageAssetRepository.save(raffleAsset);
            createdAssetIds.add(savedRaffleAsset.getId());

            FileUploadPermissionDTO raffleImagePermission = r2Service.generateUploadUrl(
                    false,
                    raffleObjectKey,
                    raffleImageMetadata.getContentType());

            // --- Prize image assets ---
            List<PrepareRaffleCreationResponseDTO.PrizeUploadSlotDTO> prizeSlots = new ArrayList<>();

            for (int i = 0; i < prizeImageMetadataList.size(); i++) {
                FileUploadRequestDTO prizeMeta = prizeImageMetadataList.get(i);

                // Necesitamos un raffleId temporal para el objectKey; usamos el assetId del
                // raffle como namespace
                String prizeObjectKey = generatePrizeObjectKey(savedRaffleAsset.getId(), prizeMeta);

                PrizeImageAsset prizeAsset = PrizeImageAsset.builder()
                        .objectKey(prizeObjectKey)
                        .sizeBytes(prizeMeta.getSizeBytes())
                        .status(AssetStatus.PENDING)
                        .build();

                PrizeImageAsset savedPrizeAsset = prizeImageAssetRepository.save(prizeAsset);
                createdAssetIds.add(savedPrizeAsset.getId());

                FileUploadPermissionDTO prizePermission = r2Service.generateUploadUrl(
                        false,
                        prizeObjectKey,
                        prizeMeta.getContentType());

                prizeSlots.add(PrepareRaffleCreationResponseDTO.PrizeUploadSlotDTO.builder()
                        .prizeIndex(i)
                        .prizeAssetId(savedPrizeAsset.getId())
                        .permission(prizePermission)
                        .build());
            }

            log.info("✅ Preparation complete. Raffle asset: {}, Prize assets: {}",
                    savedRaffleAsset.getId(),
                    prizeSlots.stream().map(s -> s.getPrizeAssetId()).toList());

            return PrepareRaffleCreationResponseDTO.builder()
                    .raffleAssetId(savedRaffleAsset.getId())
                    .raffleImagePermission(raffleImagePermission)
                    .prizeUploadSlots(prizeSlots)
                    .build();

        } catch (Exception e) {
            // Si algo falla durante el prepare, marcar los assets ya guardados como
            // huérfanos
            log.error("❌ Error during preparation, orphaning {} assets", createdAssetIds.size());
            orphanRaffleAssets(createdAssetIds);
            throw e;
        }
    }

    @Override
    public EntityCreatedResponseDTO confirmRaffleCreation(Long adminId, ConfirmRaffleCreationRequestDTO request) {

        log.info("🎫 Confirming raffle creation. Admin: {}", adminId);

        CreateRaffleRequestDTO raffleData = request.getRaffleData();

        // Re-validar datos de negocio (el frontend podría haber enviado datos
        // distintos)
        validateDates(raffleData);
        validatePrizes(raffleData.getPrizes());
        validateRules(raffleData);

        if (request.getPrizeAssetIds().size() != raffleData.getPrizes().size()) {
            throw new InvalidRequestException(
                    String.format("Expected %d prize asset IDs, got %d",
                            raffleData.getPrizes().size(),
                            request.getPrizeAssetIds().size()));
        }

        RaffleImageAsset raffleAsset = null;
        List<PrizeImageAsset> prizeAssets = new ArrayList<>();

        try {
            // --- Validar raffle image asset ---
            raffleAsset = raffleImageAssetRepository.findById(request.getRaffleAssetId())
                    .orElseThrow(() -> new InvalidRequestException(
                            "Raffle image asset not found: " + request.getRaffleAssetId()));

            if (raffleAsset.getStatus() != AssetStatus.PENDING) {
                throw new InvalidRequestException(
                        "Raffle image asset is not in PENDING state: " + raffleAsset.getStatus());
            }

            if (raffleAsset.getRaffle() != null) {
                throw new InvalidRequestException(
                        "Raffle image asset is already associated to a raffle");
            }

            log.info("Validating raffle image in R2: {}", raffleAsset.getObjectKey());

            SupportedMimeType raffleMime = r2Service.validateUploadedObject(
                    false,
                    raffleAsset.getObjectKey(),
                    raffleAsset.getSizeBytes(),
                    RaffleImagePolicy.MAX_IMAGE_SIZE_BYTES,
                    RaffleImagePolicy.ALLOWED_IMAGE_MIME_TYPES);

            raffleAsset.setMimeType(raffleMime);
            raffleAsset.setStatus(AssetStatus.VALIDATED);

            // --- Validar prize image assets ---
            for (int i = 0; i < request.getPrizeAssetIds().size(); i++) {
                Long prizeAssetId = request.getPrizeAssetIds().get(i);

                PrizeImageAsset prizeAsset = prizeImageAssetRepository.findById(prizeAssetId)
                        .orElseThrow(() -> new InvalidRequestException(
                                "Prize image asset not found: " + prizeAssetId));

                if (prizeAsset.getStatus() != AssetStatus.PENDING) {
                    throw new InvalidRequestException(
                            "Prize image asset " + prizeAssetId + " is not in PENDING state: "
                                    + prizeAsset.getStatus());
                }

                if (prizeAsset.getPrize() != null) {
                    throw new InvalidRequestException(
                            "Prize image asset " + prizeAssetId + " is already associated to a prize");
                }

                log.info("Validating prize image [{}] in R2: {}", i, prizeAsset.getObjectKey());

                SupportedMimeType prizeMime = r2Service.validateUploadedObject(
                        false,
                        prizeAsset.getObjectKey(),
                        prizeAsset.getSizeBytes(),
                        RaffleImagePolicy.MAX_IMAGE_SIZE_BYTES,
                        RaffleImagePolicy.ALLOWED_IMAGE_MIME_TYPES);

                prizeAsset.setMimeType(prizeMime);
                prizeAsset.setStatus(AssetStatus.VALIDATED);
                prizeAssets.add(prizeAsset);
            }

            // --- Construir entidades ---
            Raffle raffle = raffleMapper.toRaffle(raffleData);
            raffle.setCreatedBy(adminId);

            List<Prize> rafflePrizes = new ArrayList<>();
            for (int i = 0; i < raffleData.getPrizes().size(); i++) {
                Prize prize = prizeMapper.toPrize(raffleData.getPrizes().get(i));
                prize.setRaffle(raffle);
                rafflePrizes.add(prize);
            }

            List<RaffleRule> raffleRules = raffleData.getRules().stream()
                    .map(ruleRequest -> createRaffleRule(raffle, ruleRequest))
                    .toList();

            raffle.setPrizes(rafflePrizes);
            raffle.setRaffleRules(raffleRules);

            Raffle savedRaffle = raffleRepository.save(raffle);
            List<Prize> savedPrizes = prizeRepository.saveAll(rafflePrizes);

            // --- Asociar assets a sus entidades ---
            raffleAsset.setRaffle(savedRaffle);
            raffleImageAssetRepository.save(raffleAsset);

            for (int i = 0; i < savedPrizes.size(); i++) {
                PrizeImageAsset prizeAsset = prizeAssets.get(i);
                prizeAsset.setPrize(savedPrizes.get(i));
                prizeImageAssetRepository.save(prizeAsset);
            }

            log.info("✅ Raffle created successfully. ID: {}", savedRaffle.getId());

            return new EntityCreatedResponseDTO(
                    savedRaffle.getId(),
                    "Raffle created successfully",
                    Instant.now());

        } catch (Exception e) {
            log.error("❌ Error confirming raffle creation, orphaning assets");

            // Marcar todos los assets involucrados como huérfanos
            List<Long> assetIdsToOrphan = new ArrayList<>();
            if (raffleAsset != null)
                assetIdsToOrphan.add(raffleAsset.getId());
            prizeAssets.forEach(pa -> assetIdsToOrphan.add(pa.getId()));
            orphanRaffleAssets(assetIdsToOrphan);

            throw e;
        }
    }

    /**
     * Marca assets de raffle/prize como huérfanos en caso de error,
     * para que un job de limpieza los elimine de R2 posteriormente.
     */
    private void orphanRaffleAssets(List<Long> raffleAssetIds) {
        // Marcar RaffleImageAssets
        raffleAssetIds.forEach(id -> {
            // Intentar como RaffleImageAsset
            raffleImageAssetRepository.findById(id).ifPresent(asset -> {
                asset.setStatus(AssetStatus.ORPHANED);
                raffleImageAssetRepository.save(asset);
                try {
                    r2Service.markAsOrphan(asset.getObjectKey());
                } catch (Exception ex) {
                    log.warn("Could not mark R2 object as orphan: {}", asset.getObjectKey());
                }
            });
            // Intentar como PrizeImageAsset
            prizeImageAssetRepository.findById(id).ifPresent(asset -> {
                asset.setStatus(AssetStatus.ORPHANED);
                prizeImageAssetRepository.save(asset);
                try {
                    r2Service.markAsOrphan(asset.getObjectKey());
                } catch (Exception ex) {
                    log.warn("Could not mark R2 object as orphan: {}", asset.getObjectKey());
                }
            });
        });
    }

    private String generateRaffleObjectKey(FileUploadRequestDTO metadata) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(metadata.getOriginalFileName());

        return String.format("public/raffles/%s-%s%s",
                timestamp, uuid, extension);
    }

    private String generatePrizeObjectKey(Long raffleId, FileUploadRequestDTO metadata) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(metadata.getOriginalFileName());

        return String.format("public/prizes/raffle-%d/%s-%s%s",
                raffleId, timestamp, uuid, extension);
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot);
    }

    private void validateDates(CreateRaffleRequestDTO request) {
        if (!request.getDrawDate().isAfter(request.getEndDate())) {
            throw new InvalidRequestException("Draw date must be after end date");
        }

        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new InvalidRequestException("End date must be after start date");
        }
    }

    private void validatePrizes(List<CreatePrizeRequestDTO> prizes) {

        long uniquePositions = prizes.stream()
                .map(CreatePrizeRequestDTO::getPosition)
                .distinct()
                .count();

        if (uniquePositions != prizes.size()) {
            throw new InvalidRequestException("Prize positions must be unique");
        }

        // Validar que las posiciones sean consecutivas (opcional)
        List<Integer> positions = prizes.stream()
                .map(CreatePrizeRequestDTO::getPosition)
                .sorted()
                .toList();

        for (int i = 0; i < positions.size(); i++) {
            if (positions.get(i) != (i + 1)) {
                throw new InvalidRequestException(
                        "Prize positions must be consecutive starting from 1");
            }
        }
    }

    @SuppressWarnings("null")
    private void validateRules(CreateRaffleRequestDTO request) {

        // 1. Validar que no haya reglas duplicadas
        long uniqueRules = request.getRules().stream()
                .map(CreateRaffleRuleRequestDTO::getTicketEarningRuleId)
                .distinct()
                .count();

        if (uniqueRules != request.getRules().size()) {
            throw new InvalidRequestException("Duplicate rules are not allowed");
        }

        // 2. Validar que las reglas existan
        for (CreateRaffleRuleRequestDTO ruleRequest : request.getRules()) {
            if (!ticketEarningRuleRepository.existsById(ruleRequest.getTicketEarningRuleId())) {
                throw new InvalidRequestException(
                        "Ticket earning rule not found: " + ruleRequest.getTicketEarningRuleId());
            }
        }

        // 3. Validar que la suma de límites = maxTotalTickets
        long sumOfSourceLimits = request.getRules().stream()
                .mapToLong(CreateRaffleRuleRequestDTO::getMaxTicketsBySource)
                .sum();

        if (request.getMaxTotalTickets() != null && sumOfSourceLimits != request.getMaxTotalTickets()) {
            throw new InvalidRequestException(
                    String.format(
                            "Sum of source limits (%d) must equal maxTotalTickets (%d)",
                            sumOfSourceLimits,
                            request.getMaxTotalTickets()));
        }

        // 4. Validar maxTicketsPerUser
        if (request.getMaxTicketsPerUser() != null &&
                request.getMaxTotalTickets() != null &&
                request.getMaxTicketsPerUser() > request.getMaxTotalTickets()) {
            throw new InvalidRequestException(
                    "maxTicketsPerUser cannot exceed maxTotalTickets");
        }
    }

    @SuppressWarnings("null")
    private RaffleRule createRaffleRule(Raffle raffle, CreateRaffleRuleRequestDTO request) {
        TicketEarningRule ticketRule = ticketEarningRuleRepository.findById(request.getTicketEarningRuleId())
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Ticket earning rule with id: " + request.getTicketEarningRuleId() + " not found ",
                        TicketEarningRule.class));
        RaffleRule rule = new RaffleRule();
        rule.setRaffle(raffle);
        rule.setTicketEarningRule(ticketRule);
        rule.setMaxTicketsBySource(request.getMaxTicketsBySource());

        return rule;
    }

    @Override
    public EntityUpdatedResponseDTO updateRaffle(Long adminId, Long raffleId, UpdateRaffleRequestDTO request) {

        if (!request.getDrawDate().isAfter(request.getEndDate())) {
            throw new InvalidRequestException("Draw date must be after end date");
        }

        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new InvalidRequestException("End date must be after start date");
        }

        Raffle raffleUpdated = getRaffleById(raffleId);

        raffleUpdated.setModifiedBy(adminId);
        raffleUpdated.setTitle(request.getTitle());
        raffleUpdated.setDescription(request.getDescription());
        raffleUpdated.setRaffleType(request.getRaffleType());
        raffleUpdated.setRequiresPet(request.getRequiresPet());
        raffleUpdated.setStartDate(request.getStartDate());
        raffleUpdated.setEndDate(request.getEndDate());
        raffleUpdated.setDrawDate(request.getDrawDate());

        raffleRepository.save(raffleUpdated);

        return new EntityUpdatedResponseDTO(raffleUpdated.getId(), "Raffle updated successfully", Instant.now());
    }

    @Override
    public void activateRaffle(Long raffleId) {

        Raffle raffle = getRaffleById(raffleId);

        if (raffle.getRaffleStatus() != RaffleStatus.DRAFT && raffle.getRaffleStatus() != RaffleStatus.CANCELLED) {
            throw new InvalidOperationException("Only 'DRAFT' or 'CANCELLED' raffles may be activated");
        }

        if (raffle.getPrizes() == null || raffle.getPrizes().isEmpty()) {
            throw new InvalidOperationException("Cannot activate raffle without prizes");
        }

        if (ZonedDateTime.now().isAfter(raffle.getEndDate())) {
            throw new InvalidOperationException("Cannot activate expired raffle");
        }

        raffle.setRaffleStatus(RaffleStatus.ACTIVE);
        raffleRepository.save(raffle);
    }

    @Override
    public void closeRaffle(Long raffleId) {

        Raffle raffle = getRaffleById(raffleId);
        if (raffle.getRaffleStatus() != RaffleStatus.ACTIVE) {
            throw new InvalidOperationException("Raffle must be 'ACTIVE' to set status 'CLOSED'");
        }
        raffle.setRaffleStatus(RaffleStatus.CLOSED);
        raffleRepository.save(raffle);
    }

    @Override
    public void liveRaffle(Long raffleId) {

        Raffle raffle = getRaffleById(raffleId);
        if (raffle.getRaffleStatus() != RaffleStatus.CLOSED) {
            throw new InvalidOperationException("Raffle must be 'CLOSED' to set status 'LIVE'");
        }

        raffle.setRaffleStatus(RaffleStatus.LIVE);
        raffleRepository.save(raffle);

    }

    @Override
    public void cancelRaffle(Long raffleId) {

        Raffle raffle = getRaffleById(raffleId);
        if (raffle.getRaffleStatus() != RaffleStatus.ACTIVE) {
            throw new InvalidOperationException("Raffle must be 'ACTIVE' to set status 'CANCELLED'");
        }

        raffle.setRaffleStatus(RaffleStatus.LIVE);
        raffleRepository.save(raffle);
    }

    @Override
    public void deleteRaffle(Long raffleId) {

        Raffle raffle = getRaffleById(raffleId);
        if (raffle.getRaffleStatus() != RaffleStatus.DRAFT) {
            throw new InvalidOperationException("Only 'DRAFT' raffles may be deleted");
        }

        raffleRepository.delete(raffle);
    }

    @Override
    public Raffle getRaffleById(Long raffleId) {

        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        return raffleRepository.findById(raffleId).orElseThrow(
                () -> new ObjectNotFoundException("Raffle with id: " + raffleId + " not found ", Raffle.class));
    }

    @Override
    public RaffleResponseDTO getRaffleResponseDTOById(Long raffleId) {

        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        Raffle raffle = raffleRepository.findById(raffleId).orElseThrow(
                () -> new ObjectNotFoundException("Raffle with id: " + raffleId + " not found ", Raffle.class));

        RaffleImageAsset asset = raffleImageAssetRepository.findByRaffleId(raffleId).orElseThrow(
                () -> new ObjectNotFoundException("Raffle asset with raffle id: " + raffleId + " not found ",
                        RaffleImageAsset.class));

        RaffleResponseDTO response = raffleMapper.toRaffleResponseDTO(raffle);
        response.setImageUrl(domain + asset.getObjectKey());
        for (PrizeResponseDTO p : response.getPrizes()) {
            p.setImageUrl(domain + p.getImageUrl());
        }
        return response;
    }

    @Override
    public PagedResponse<RaffleSummaryResponseDTO> getSummaryRafflesByStatusAndType(RaffleStatus status,
            RaffleType type, Pageable pageable) {

        Page<RaffleSummaryResponseDTO> rafflesFound = raffleRepository.findByRaffleStatusAndRaffleType(status, type,
                pageable);
        rafflesFound.forEach(r -> r.setImageUrl(domain + r.getImageUrl()));
        return PagedResponse.from(rafflesFound);
    }

    @Override
    public RaffleStatsResponseDTO getRaffleStats(Long raffleId) {

        Raffle raffle = getRaffleById(raffleId);
        RaffleStatsResponseDTO response = raffleMapper.toRaffleStatsResponseDTO(raffle);

        List<Object[]> stats = raffleTicketRepository.countTicketsBySource(raffleId);

        Map<RaffleTicketSource, Long> ticketsBySource = stats.stream()
                .collect(Collectors.toMap(
                        entry -> (RaffleTicketSource) entry[0],
                        entry -> (Long) entry[1]));

        response.setTicketsBySource(ticketsBySource);

        return response;

    }

    @Override
    public List<ParticipantLeaderboardDTO> getRaffleLeaderBoard(Long raffleId) {
        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        return raffleParticipationRepository.findLeaderboard(raffleId, PageRequest.of(0, 10));
    }

    @Override
    public List<Raffle> getActiveRafflesOrderedByDrawDate(ZonedDateTime drawDate) {
        return raffleRepository.findActiveRaffleByDrawDate(drawDate);
    }

    @Override
    public Long countRafflesByStatus(RaffleStatus status) {
        return raffleRepository.countByRaffleStatus(status);
    }

    @Override
    public List<RaffleSummaryResponseDTO> getLiveRaffles() {
        List<RaffleSummaryResponseDTO> lives = raffleRepository.findLiveRaffles();
        lives.forEach(r -> r.setImageUrl(domain + r.getImageUrl()));
        return lives;
    }

    @Override
    public PagedResponse<RaffleSummaryResponseDTO> getActiveRaffles(RaffleType type, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, 10);
        Page<RaffleSummaryResponseDTO> actives = raffleRepository.findActiveRaffles(type, pageable);
        actives.forEach(r -> r.setImageUrl(domain + r.getImageUrl()));
        return PagedResponse.from(actives);
    }

}
