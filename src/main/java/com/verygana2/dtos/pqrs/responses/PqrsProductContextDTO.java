package com.verygana2.dtos.pqrs.responses;

import com.verygana2.models.enums.marketplace.ProductType;

import lombok.Data;

/** Contexto del producto en disputa, para que el admin resuelva un PQRS de marketplace sin salir del detalle. */
@Data
public class PqrsProductContextDTO {
    private Long id;
    private String name;
    private String description;
    private String categoryName;
    private Long priceCents;
    private ProductType productType;
    private String imageUrl;
    private Double averageRate;
    private Integer reviewCount;
}
