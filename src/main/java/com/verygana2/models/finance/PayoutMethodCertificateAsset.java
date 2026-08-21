package com.verygana2.models.finance;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.SupportedMimeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Certificación bancaria (PDF o foto) que respalda un PayoutMethod BANK_ACCOUNT
 * durante la revisión manual del admin. Mismo patrón que ProductImageAsset:
 * se sube a R2 vía URL pre-firmada antes de asociarse a la entidad final.
 */
@Entity
@Table(name = "payout_method_certificate_assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutMethodCertificateAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private SupportedMimeType mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status;

    @OneToOne
    @JoinColumn(name = "payout_method_id")
    private PayoutMethod payoutMethod;

    @Column(nullable = false)
    private ZonedDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = AssetStatus.PENDING;
        }
    }
}
