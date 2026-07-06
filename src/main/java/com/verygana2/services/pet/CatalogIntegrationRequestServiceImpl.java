package com.verygana2.services.pet;

import com.verygana2.dtos.pet.CatalogIntegrationRequestDTO;
import com.verygana2.dtos.pet.CatalogIntegrationResponseDTO;
import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.pets.CatalogIntegrationRequest;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.pet.CatalogIntegrationRequestRepository;
import com.verygana2.services.interfaces.pet.CatalogIntegrationRequestService;
import com.verygana2.services.interfaces.pet.PetCatalogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogIntegrationRequestServiceImpl implements CatalogIntegrationRequestService {

    @Value("${cloudflare.r2.pets-cdn-domain:}")
    private String petsCdnDomain;

    @Value("${cloudflare.r2.pets-bucket-name:verygana-pets}")
    private String petsBucketName;

    private final CatalogIntegrationRequestRepository requestRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final PetCatalogService catalogService;

    @Override
    public CatalogIntegrationResponseDTO submit(Long userId, CatalogIntegrationRequestDTO dto) {
        CommercialDetails commercial = commercialDetailsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found for userId=" + userId));

        CatalogIntegrationRequest request = new CatalogIntegrationRequest();
        request.setCommercial(commercial);
        request.setProductName(dto.productName());
        request.setDescription(dto.description());
        request.setImageObjectKey(dto.imageObjectKey());
        request.setDesiredEffects(dto.desiredEffects());
        request.setStatus(CatalogRequestStatus.PENDING);

        return toResponse(requestRepository.save(request));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogIntegrationResponseDTO> getMyRequests(Long userId) {
        CommercialDetails commercial = commercialDetailsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found for userId=" + userId));

        return requestRepository.findByCommercial_IdOrderByCreatedAtDesc(commercial.getId())
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogIntegrationResponseDTO> getAllRequests() {
        return requestRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogIntegrationResponseDTO> getRequestsByStatus(CatalogRequestStatus status) {
        return requestRepository.findByStatusOrderByCreatedAtAsc(status)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public CatalogIntegrationResponseDTO markInReview(Long requestId) {
        CatalogIntegrationRequest request = findOrThrow(requestId);
        assertStatus(request, CatalogRequestStatus.PENDING, "Solo se pueden revisar solicitudes en estado PENDING");
        request.setStatus(CatalogRequestStatus.IN_REVIEW);
        return toResponse(requestRepository.save(request));
    }

    @Override
    public CatalogIntegrationResponseDTO approve(Long requestId, PetCatalogItemRequestDTO catalogItemDto) {
        CatalogIntegrationRequest request = findOrThrow(requestId);
        assertNotFinal(request);

        var catalogItem = catalogService.createCatalogItem(catalogItemDto);

        request.setStatus(CatalogRequestStatus.APPROVED);
        request.setResultCatalogItemId(catalogItem.id());
        return toResponse(requestRepository.save(request));
    }

    @Override
    public CatalogIntegrationResponseDTO reject(Long requestId, String reason) {
        CatalogIntegrationRequest request = findOrThrow(requestId);
        assertNotFinal(request);

        request.setStatus(CatalogRequestStatus.REJECTED);
        request.setRejectionReason(reason);
        return toResponse(requestRepository.save(request));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CatalogIntegrationRequest findOrThrow(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada id=" + id));
    }

    private void assertStatus(CatalogIntegrationRequest r, CatalogRequestStatus expected, String msg) {
        if (r.getStatus() != expected) {
            throw new IllegalStateException(msg + ". Estado actual: " + r.getStatus());
        }
    }

    private void assertNotFinal(CatalogIntegrationRequest r) {
        if (r.getStatus() == CatalogRequestStatus.APPROVED || r.getStatus() == CatalogRequestStatus.REJECTED) {
            throw new IllegalStateException("La solicitud ya fue finalizada con estado: " + r.getStatus());
        }
    }

    private String buildPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return null;
        if (petsCdnDomain != null && !petsCdnDomain.isBlank())
            return String.format("https://%s/%s", petsCdnDomain, objectKey);
        return String.format("https://%s.r2.dev/%s", petsBucketName, objectKey);
    }

    private CatalogIntegrationResponseDTO toResponse(CatalogIntegrationRequest r) {
        return new CatalogIntegrationResponseDTO(
                r.getId(),
                r.getCommercial().getCompanyName(),
                r.getProductName(),
                r.getDescription(),
                buildPublicUrl(r.getImageObjectKey()),
                r.getDesiredEffects(),
                r.getStatus(),
                r.getRejectionReason(),
                r.getResultCatalogItemId(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}