package com.verygana2.controllers.compliance;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.models.User;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;
import com.verygana2.repositories.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/compliance/kyc")
@PreAuthorize("hasRole('ROLE_COMPLIANCE_OFFICER')")
@RequiredArgsConstructor
public class ComplianceKycController {

    private final UserRepository userRepository;

    public record KycPendingDTO(
            Long id,
            String email,
            String phoneNumber,
            Role role,
            ZonedDateTime registeredDate,
            // campos KYC — presentes dependiendo del rol
            String name,
            String lastName,
            String documentType,
            String documentNumber,
            Boolean esPEP,
            // solo comerciales
            String companyName,
            String nit,
            String codigoCIIU,
            String representanteDocType,
            String representanteDocNumero
    ) {}

    @GetMapping("/pending")
    public ResponseEntity<List<KycPendingDTO>> getPendingKycReview() {
        List<User> users = userRepository.findByUserState(UserState.PENDING_KYC_REVIEW);

        List<KycPendingDTO> dtos = users.stream().map(u -> {
            String name = null, lastName = null, docType = null, docNumber = null;
            Boolean esPEP = null;
            String companyName = null, nit = null, ciiu = null, repDocType = null, repDocNum = null;

            if (u.getUserDetails() instanceof com.verygana2.models.userDetails.ConsumerDetails d) {
                name = d.getName();
                lastName = d.getLastName();
                docType = d.getDocumentType() != null ? d.getDocumentType().name() : null;
                docNumber = d.getDocumentNumber();
                esPEP = d.isEsPEP();
            } else if (u.getUserDetails() instanceof com.verygana2.models.userDetails.CommercialDetails d) {
                companyName = d.getCompanyName();
                nit = d.getNit();
                ciiu = d.getCodigoCIIU();
                repDocType = d.getRepresentanteDocType() != null ? d.getRepresentanteDocType().name() : null;
                repDocNum = d.getRepresentanteDocNumero();
                esPEP = d.isEsPEP();
            }

            return new KycPendingDTO(
                    u.getId(), u.getEmail(), u.getPhoneNumber(), u.getRole(), u.getRegisteredDate(),
                    name, lastName, docType, docNumber, esPEP,
                    companyName, nit, ciiu, repDocType, repDocNum
            );
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{userId}/approve")
    public ResponseEntity<Void> approveKyc(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        if (user.getUserState() != UserState.PENDING_KYC_REVIEW) {
            return ResponseEntity.badRequest().build();
        }

        user.setUserState(UserState.ACTIVE);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<Void> rejectKyc(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "KYC rechazado por el oficial de cumplimiento") String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        if (user.getUserState() != UserState.PENDING_KYC_REVIEW) {
            return ResponseEntity.badRequest().build();
        }

        user.setUserState(UserState.BLOCKED);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}
