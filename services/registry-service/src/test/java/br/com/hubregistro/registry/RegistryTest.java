package br.com.hubregistro.registry;

import br.com.hubregistro.commons.document.Cnpj;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.registry.domain.Address;
import br.com.hubregistro.registry.domain.Registry;
import br.com.hubregistro.registry.domain.RegistryStatus;
import br.com.hubregistro.registry.domain.ServiceArea;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistryTest {

    private static final Address ADDRESS = new Address("Av. Paulista", "1000", null, "Bela Vista", "São Paulo",
            "3550308", "sp", "01310-100");

    private static Registry newRegistry(Set<String> types, Set<ServiceArea> areas) {
        return Registry.create("Reg Alfa Ltda.", "Reg Alfa", new Cnpj("12ABC34501DE35"), "reg_alfa", ADDRESS, null,
                types, areas, List.of());
    }

    @Test
    void startsInactiveAndCannotBeActivatedWithoutCoverage() {
        Registry registry = newRegistry(Set.of(), Set.of());

        assertThat(registry.status()).isEqualTo(RegistryStatus.INACTIVE);
        assertThatThrownBy(() -> registry.changeStatus(RegistryStatus.ACTIVE, "Pronta", UUID.randomUUID(), Instant.now()))
                .hasFieldOrPropertyWithValue("code", "coverage_required");
    }

    @Test
    void activeRegistryCannotLoseItsCoverage() {
        Registry registry = newRegistry(Set.of("residential"), Set.of(new ServiceArea("SP", null)));
        registry.changeStatus(RegistryStatus.ACTIVE, "Integração validada", UUID.randomUUID(), Instant.now());

        assertThat(registry.contractTypes()).containsExactly("RESIDENTIAL");
        assertThatThrownBy(() -> registry.defineCoverage(Set.of("RESIDENTIAL"), Set.of()))
                .hasFieldOrPropertyWithValue("code", "coverage_required");
    }

    @Test
    void servesWholeStatesOrSpecificMunicipalities() {
        Registry registry = newRegistry(Set.of("RESIDENTIAL"),
                Set.of(new ServiceArea("SP", null), new ServiceArea("MG", "3106200")));

        assertThat(registry.serves("SP", "3509502")).isTrue();
        assertThat(registry.serves("MG", "3106200")).isTrue();
        assertThat(registry.serves("MG", "3170206")).isFalse();
        assertThat(registry.serves("RJ", "3304557")).isFalse();
    }

    @Test
    void validatesAddressAndAreas() {
        assertThat(ADDRESS.uf()).isEqualTo("SP");
        assertThat(ADDRESS.zipCode()).isEqualTo("01310100");
        assertThatThrownBy(() -> new ServiceArea("XX", null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new ServiceArea("SP", "123")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Address("R", "1", null, "B", "C", "123", "SP", "01310100")).isInstanceOf(DomainException.class);
    }
}
