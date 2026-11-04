package sn.senkyc.api.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Le périmètre produit est figé : toute évolution doit être une décision explicite (et migrer le schéma). */
class ScopeTest {

    @Test
    void onlyThreeDocumentTypesAreSupported() {
        assertThat(Arrays.stream(DocumentType.values()).map(Enum::name))
                .containsExactlyInAnyOrder("NATIONAL_ID", "PASSPORT", "DRIVING_LICENSE");
    }

    @Test
    void countriesAreTheEightUemoaMembers() {
        assertThat(Arrays.stream(IssuingCountry.values()).map(Enum::name))
                .containsExactlyInAnyOrder("SN", "BJ", "BF", "CI", "GW", "ML", "NE", "TG");
    }

    @Test
    void enumNamesMatchTheDatabaseConstraints() {
        Set<String> dbTypes = Set.of("NATIONAL_ID", "PASSPORT", "DRIVING_LICENSE");
        assertThat(Arrays.stream(DocumentType.values()).map(Enum::name)).allMatch(dbTypes::contains);
    }
}
