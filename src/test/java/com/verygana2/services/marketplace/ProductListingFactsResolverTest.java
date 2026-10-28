package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.repositories.marketplace.ProductListingFacts;
import com.verygana2.repositories.marketplace.ProductRepository;

class ProductListingFactsResolverTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ProductListingFactsResolver resolver = new ProductListingFactsResolver(productRepository, clock);

    @Test
    @DisplayName("sin ids no consulta la base")
    void doesNotQueryWithEmptyIds() {
        assertThat(resolver.resolve(List.of())).isEmpty();

        verify(productRepository, never()).findListingFacts(any(), any());
    }

    @Test
    @DisplayName("un id que la consulta no devuelve queda con stock 0")
    void missingIdMeansZero() {
        when(productRepository.findListingFacts(any(), any())).thenReturn(List.of());

        Map<Long, ProductListingFacts> facts = resolver.resolve(List.of(5L));

        assertThat(facts.get(5L).availableStock()).isZero();
        assertThat(facts.get(5L).productId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("el 'ahora' de la consulta sale del Clock")
    void passesNowFromClock() {
        when(productRepository.findListingFacts(any(), any())).thenReturn(List.of());

        resolver.resolve(List.of(1L));

        verify(productRepository).findListingFacts(any(), org.mockito.ArgumentMatchers.eq(ZonedDateTime.now(clock)));
    }

    @Test
    @DisplayName("una sola consulta para todos los ids, sin repetidos")
    void oneQueryForAllIds() {
        UUID publicId = UUID.randomUUID();
        when(productRepository.findListingFacts(any(), any())).thenReturn(List.of(
                new ProductListingFacts(1L, "A", publicId, 3L),
                new ProductListingFacts(2L, "B", publicId, 0L)));

        Map<Long, ProductListingFacts> facts = resolver.resolve(List.of(1L, 2L, 2L));

        verify(productRepository, times(1)).findListingFacts(Set.of(1L, 2L), ZonedDateTime.now(clock));
        assertThat(facts).containsOnlyKeys(1L, 2L);
        assertThat(facts.get(1L).availableStock()).isEqualTo(3L);
    }
}
