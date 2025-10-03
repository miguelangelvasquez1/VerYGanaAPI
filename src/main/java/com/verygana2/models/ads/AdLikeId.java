package com.verygana2.models.ads;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Embeddable
public class AdLikeId implements Serializable { //Porque el usuario solo puede dar like una vez por anuncio

    private Long userId;
    private Long adId;

    // equals() y hashCode() obligatorios para JPA
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdLikeId)) return false;
        AdLikeId that = (AdLikeId) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(adId, that.adId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, adId);
    }
}
