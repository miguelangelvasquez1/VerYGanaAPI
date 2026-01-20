package com.verygana2.models.products;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "favorite_products", 
uniqueConstraints = @UniqueConstraint(columnNames = {"consumer_id", "product_id"}),
indexes = {
    @Index(name = "idx_consumer_id", columnList = "consumer_id"),
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FavoriteProduct {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false)
    private ConsumerDetails consumer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }
}
