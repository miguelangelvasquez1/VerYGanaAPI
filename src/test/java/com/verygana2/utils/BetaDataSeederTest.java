package com.verygana2.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BetaDataSeeder - seeds de referencia y demo")
class BetaDataSeederTest {

    @Test
    @DisplayName("carga todos los datos de referencia sin ejecutar antes de crear la wallet los ítems comerciales")
    void loadsReferenceSeedsAndDefersWalletDependentPetItems() {
        List<String> scripts = BetaDataSeeder.seedScripts();

        assertThat(scripts).containsAll(ReferenceSeedScripts.all()
                .stream()
                .filter(path -> !path.equals(ReferenceSeedScripts.PET_COMMERCIAL_ITEMS))
                .toList());
        assertThat(scripts).doesNotHaveDuplicates()
                .noneMatch(path -> path.startsWith("db/seed/test/"))
                .containsSubsequence(
                        "db/seed/beta/beta_users.sql",
                        "db/seed/beta/beta_finance.sql",
                        ReferenceSeedScripts.PET_COMMERCIAL_ITEMS);
    }
}
