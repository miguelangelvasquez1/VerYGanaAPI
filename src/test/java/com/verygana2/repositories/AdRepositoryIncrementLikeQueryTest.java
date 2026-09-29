package com.verygana2.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * Guarda estructural de {@link AdRepository#incrementLikeIfAvailable}.
 *
 * <p>MySQL/MariaDB evalúan las asignaciones de un {@code UPDATE} de izquierda a derecha usando
 * los valores YA actualizados; PostgreSQL y H2 usan los originales. Por eso este bug no se puede
 * reproducir con los tests de H2: con {@code currentLikes = currentLikes + 1} primero, los
 * {@code CASE} de {@code status}/{@code endDate} veían el contador ya incrementado y el anuncio
 * pasaba a COMPLETED con un like de menos (con 9 de 10 quedaba cerrado y el último like se
 * rechazaba por {@code status = ACTIVE}). Se comprobó contra MariaDB 10.4 el 2026-09-26.
 *
 * <p>Como el motor de tests no distingue el orden, este test fija la estructura de la query: toda
 * lectura de {@code currentLikes} dentro del {@code SET} debe ir ANTES de la asignación que lo
 * incrementa.
 */
@DisplayName("AdRepository.incrementLikeIfAvailable — orden del SET (compatibilidad MySQL)")
class AdRepositoryIncrementLikeQueryTest {

    private static String setClause() throws NoSuchMethodException {
        String jpql = AdRepository.class
                .getMethod("incrementLikeIfAvailable", Long.class, ZonedDateTime.class)
                .getAnnotation(Query.class)
                .value();
        int set = jpql.indexOf("SET");
        int where = jpql.indexOf("WHERE");
        assertThat(set).as("la query debe tener SET").isPositive();
        assertThat(where).as("la query debe tener WHERE").isGreaterThan(set);
        return jpql.substring(set, where);
    }

    @Test
    @DisplayName("el contador se asigna DESPUÉS de todos los CASE que lo leen")
    void counterIsAssignedAfterEveryExpressionThatReadsIt() throws Exception {
        String set = setClause();

        int counterAssignment = set.indexOf("a.currentLikes = ");
        int lastRead = set.lastIndexOf("a.currentLikes + 1 >=");

        assertThat(counterAssignment).as("debe existir la asignación del contador").isNotNegative();
        assertThat(lastRead).as("debe existir la lectura del contador en los CASE").isNotNegative();
        assertThat(lastRead)
                .as("en MySQL las asignaciones usan valores ya actualizados: el contador va al final")
                .isLessThan(counterAssignment);
    }

    @Test
    @DisplayName("status y endDate se calculan antes de incrementar el contador")
    void statusAndEndDateComeBeforeTheCounter() throws Exception {
        String set = setClause();

        int counterAssignment = set.indexOf("a.currentLikes = ");

        assertThat(set.indexOf("a.status = ")).isNotNegative().isLessThan(counterAssignment);
        assertThat(set.indexOf("a.endDate = ")).isNotNegative().isLessThan(counterAssignment);
    }
}
