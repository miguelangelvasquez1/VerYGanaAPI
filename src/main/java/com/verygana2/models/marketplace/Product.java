package com.verygana2.models.marketplace;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.exceptions.InsufficientStockException;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_commercial_id", columnList = "commercial_id"),
        @Index(name = "idx_product_category_id", columnList = "product_category_id"),
        @Index(name = "idx_price", columnList = "price_cents"),
        @Index(name = "idx_average_rate", columnList = "average_rate"),
        @Index(name = "idx_status", columnList = "status")
})
@Getter
@Setter
@ToString(exclude = { "commercial", "reviews", "stockItems", "productCategory" })
@EqualsAndHashCode(exclude = { "commercial", "reviews", "stockItems", "productCategory" })
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<ProductReview> reviews = new ArrayList<>();

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "average_rate")
    private Double averageRate;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @OneToOne(mappedBy = "product", fetch = FetchType.LAZY)
    private ProductImageAsset imageAsset;

    /**
     * Precio total del producto en centavos de COP.
     *
     * POR QUÉ Long EN LUGAR DE BigDecimal:
     * Los productos digitales en Colombia siempre tienen precios enteros (sin
     * fracción de centavo). Long es más eficiente en memoria y en índices de BD.
     * Wompi también trabaja en centavos enteros, así que no hay conversión.
     *
     * Este es el precio TOTAL que el usuario debe cubrir entre llaves + dinero real.
     * Ejemplo: producto de $15.000 COP → priceCents = 1_500_000
     *
     * Nota: si el equipo prefiere mantener BigDecimal por consistencia con el
     * resto de entidades del proyecto, es válido. Lo importante es NO mezclar
     * ambos en la misma operación financiera sin conversión explícita.
     */
    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    /**
     * Porcentaje máximo del precio que el usuario puede cubrir con llaves.
     * Se hereda del plan del empresario al momento de PUBLICAR el producto
     * y se persiste como snapshot inmutable.
     *
     * POR QUÉ snapshot y no calculado en runtime:
     * Si el empresario sube de plan después de publicar, los productos existentes
     * no deben cambiar su proporción retroactivamente. El usuario que ve el producto
     * debe ver siempre las mismas condiciones de pago.
     *
     * Valores según plan:
     *   BASIC    → 20
     *   STANDARD → 35
     *   PREMIUM  → 50
     *
     * Ejemplo con priceCents = 1_500_000 y maxKeysPct = 40:
     *   maxKeysValue = 1_500_000 × 40 / 100 = 600_000 centavos = $6.000 COP
     *   maxKeysCount = 600_000 / 10 = 60_000 llaves  (1 llave = $10 COP = 1_000 centavos)
     *   minCashCents = 1_500_000 - 600_000 = 900_000 centavos = $9.000 COP
     */
    @Column(name = "max_keys_pct", nullable = false)
    private Integer maxKeysPct;

    @Transient
    private Integer stock;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductStock> stockItems = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FavoriteProduct> favoritedBy = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_category_id", nullable = false)
    private ProductCategory productCategory;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Auditoria

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AdminDetails approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private AdminDetails rejectedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private AdminDetails deletedBy;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "approved_at")
    private ZonedDateTime approvedAt;

    @Column(name = "rejected_at")
    private ZonedDateTime rejectedAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Column(name = "rejected_until")
    private ZonedDateTime rejectedUntil; // hasta cuándo está bloqueado para reenvío

    @Column(name = "resubmitted_at")
    private ZonedDateTime resubmittedAt; // cuando el commercial lo reenvía a revisión

    @Column(name = "resubmission_count")
    private Integer resubmissionCount; // cuántas veces ha sido reenviado, útil para detectar abuso

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "deletion_reason", columnDefinition = "TEXT")
    private String deletionReason;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
        this.averageRate = 0.0;
        this.reviewCount = 0;
        this.status = ProductStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public String getImageUrl() {
        if (this.imageAsset == null)
            return null;
        return "https://cdn.verygana.com/public/" + this.imageAsset.getObjectKey();
    }

    public Integer getAvailableStock() {
        return (int) stockItems.stream()
                .filter(stock -> stock.getStatus() == StockStatus.AVAILABLE && !stock.isExpired()).count();
    }

    public void updateStockCount() {
        this.stock = getAvailableStock();
    }

    public ProductStock getNextAvailableCode() {
        return stockItems.stream().filter(stock -> stock.getStatus() == StockStatus.AVAILABLE && !stock.isExpired())
                .findFirst().orElseThrow(() -> new InsufficientStockException(this.name));
    }

    public void updateAverageRating() {
        List<ProductReview> visibleReviews = reviews.stream()
                .filter(ProductReview::isVisible)
                .toList();

        this.reviewCount = visibleReviews.size();

        if (reviewCount == 0) {
            this.averageRate = 0.0;
        } else {
            this.averageRate = visibleReviews.stream()
                    .mapToInt(ProductReview::getRating)
                    .average()
                    .orElse(0.0);
        }
    }

    /**
     * Calcula el máximo de llaves que se pueden usar para pagar este producto.
     * Útil en el frontend y en CopaymentService para validar el copago.
     *
     * @return cantidad máxima de llaves (1 llave = $10 COP = 1.000 centavos)
     */
    public long getMaxKeysAllowed() {
        long maxKeysValueCents = priceCents * maxKeysPct / 100;
        return maxKeysValueCents / 1_000; // 1 llave = 1.000 centavos ($10 COP)
    }
 
    /**
     * Calcula el mínimo que el usuario debe pagar con dinero real (Wompi).
     *
     * @return centavos de COP mínimos en efectivo
     */
    public long getMinCashCents() {
        return priceCents - (priceCents * maxKeysPct / 100);
    }
}
