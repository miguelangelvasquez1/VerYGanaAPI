package com.verygana2.services.tax;

import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.tax.TaxProfileDTOs.Establishment;
import com.verygana2.dtos.tax.TaxRequestDTOs.CloseEstablishmentRequest;
import com.verygana2.dtos.tax.TaxRequestDTOs.EstablishmentRequest;
import com.verygana2.event.TaxProfileChangedEvent;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.Municipality;
import com.verygana2.models.enums.tax.EstablishmentStatus;
import com.verygana2.models.enums.tax.TaxProfileChangeType;
import com.verygana2.models.tax.CommercialEstablishment;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.tax.CommercialEstablishmentRepository;
import com.verygana2.services.LocationService;
import com.verygana2.services.interfaces.tax.CommercialEstablishmentService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CommercialEstablishmentServiceImpl implements CommercialEstablishmentService {

    /** Una URL, un correo o un usuario de red social es un canal digital, no una sede. */
    private static final Pattern DIGITAL_CHANNEL = Pattern.compile(
            "(?i)(https?://|www\\.|\\.com\\b|\\.co\\b|@)");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private final CommercialEstablishmentRepository establishmentRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final LocationService locationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<Establishment> list(Long commercialId) {
        return establishmentRepository.findByCommercial_IdOrderByCreatedAtDesc(commercialId).stream()
                .map(TaxProfileViews::establishment).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Establishment> listActive(Long commercialId) {
        return establishmentRepository.findByCommercial_IdAndStatus(commercialId, EstablishmentStatus.ACTIVE).stream()
                .map(TaxProfileViews::establishment).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int countActive(Long commercialId) {
        return establishmentRepository.countByCommercial_IdAndStatus(commercialId, EstablishmentStatus.ACTIVE);
    }

    @Override
    public Establishment create(Long commercialId, EstablishmentRequest request) {
        String address = request.getAddress().trim();
        if (DIGITAL_CHANNEL.matcher(address).find()) {
            throw new InvalidRequestException(
                    "Indique la dirección física de la sede. Un sitio web, correo o red social no es un establecimiento.");
        }
        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Commercial not found: " + commercialId));
        Municipality municipality = locationService.getMunicipalityEntityByCode(request.getMunicipalityCode());

        String normalized = normalizeAddress(address);
        if (establishmentRepository.existsByCommercial_IdAndStatusAndAddressNormalizedAndMunicipality_Code(
                commercialId, EstablishmentStatus.ACTIVE, normalized, municipality.getCode())) {
            throw new InvalidRequestException(
                    "Ya tiene un establecimiento activo con esa dirección en ese municipio. "
                            + "Si es la misma sede, no la registre dos veces.");
        }

        CommercialEstablishment establishment = new CommercialEstablishment();
        establishment.setCommercial(commercial);
        establishment.setName(request.getName().trim());
        establishment.setAddress(address);
        establishment.setAddressNormalized(normalized);
        establishment.setMunicipality(municipality);
        establishment.setRelation(request.getRelation());
        establishment.setStatus(EstablishmentStatus.ACTIVE);
        CommercialEstablishment saved = establishmentRepository.save(establishment);

        eventPublisher.publishEvent(new TaxProfileChangedEvent(this, commercialId,
                TaxProfileChangeType.ESTABLISHMENT_CHANGE, "Nuevo establecimiento " + saved.getId()));
        return TaxProfileViews.establishment(saved);
    }

    @Override
    public Establishment close(Long commercialId, Long establishmentId, CloseEstablishmentRequest request) {
        CommercialEstablishment establishment = establishmentRepository.findByIdAndCommercial_Id(establishmentId, commercialId)
                .orElseThrow(() -> new EntityNotFoundException("Establecimiento no encontrado: " + establishmentId));
        if (establishment.getStatus() == EstablishmentStatus.CLOSED) {
            throw new InvalidRequestException("Este establecimiento ya está cerrado.");
        }
        establishment.setStatus(EstablishmentStatus.CLOSED);
        establishment.setClosedAt(ZonedDateTime.now());
        establishment.setClosedReason(request.getReason().trim());
        establishmentRepository.save(establishment);

        eventPublisher.publishEvent(new TaxProfileChangedEvent(this, commercialId,
                TaxProfileChangeType.ESTABLISHMENT_CHANGE, "Establecimiento " + establishmentId + " cerrado"));
        return TaxProfileViews.establishment(establishment);
    }

    /** "Cra. 14 #20-30 Local 2" y "cra 14 # 20 - 30 local 2" son la misma dirección. */
    static String normalizeAddress(String address) {
        String withoutAccents = DIACRITICS.matcher(Normalizer.normalize(address, Normalizer.Form.NFD)).replaceAll("");
        return NON_ALPHANUMERIC.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
