package br.com.hubregistro.auth.domain;

import br.com.hubregistro.auth.domain.client.ApiClient;
import br.com.hubregistro.auth.domain.reset.PasswordToken;
import br.com.hubregistro.auth.domain.reset.TokenPurpose;
import br.com.hubregistro.commons.crypto.SecureTokens;
import br.com.hubregistro.commons.error.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiClientTest {

    private static final Instant T0 = Instant.parse("2026-11-02T12:00:00Z");

    @Test
    void onlyContractAndDocumentScopesCanBeGranted() {
        assertThatThrownBy(() -> ApiClient.create(UUID.randomUUID(), "ERP", "hub_x", "h", Set.of("user:manage"), null, null, T0))
                .isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("code", "invalid_scope");
    }

    @Test
    void tokenScopesAreTheRequestedSubsetOrAllWhenNoneRequested() {
        ApiClient client = ApiClient.create(UUID.randomUUID(), "ERP", "hub_x", "h",
                Set.of("contract:read", "contract:submit"), null, null, T0);

        assertThat(client.grantScopes(Set.of())).containsExactlyInAnyOrder("contract:read", "contract:submit");
        assertThat(client.grantScopes(Set.of("contract:read"))).containsExactly("contract:read");
        assertThatThrownBy(() -> client.grantScopes(Set.of("contract:cancel"))).hasFieldOrPropertyWithValue("code", "invalid_scope");
    }

    @Test
    void revokedOrExpiredClientIsNotUsable() {
        ApiClient client = ApiClient.create(UUID.randomUUID(), "ERP", "hub_x", "h", Set.of("contract:read"), 100,
                T0.plus(Duration.ofDays(30)), T0);

        assertThat(client.isUsable(T0)).isTrue();
        assertThat(client.isUsable(T0.plus(Duration.ofDays(30)))).isFalse();
        client.revoke();
        assertThat(client.isUsable(T0)).isFalse();
    }

    @Test
    void emailLinksAreSingleUseAndExpire() {
        PasswordToken reset = PasswordToken.issue(UUID.randomUUID(), SecureTokens.sha256("r"), TokenPurpose.RESET, T0, null);
        reset.consume(T0.plusSeconds(30));
        assertThatThrownBy(() -> reset.consume(T0.plusSeconds(31))).hasFieldOrPropertyWithValue("code", "invalid_token");

        PasswordToken activation = PasswordToken.issue(UUID.randomUUID(), SecureTokens.sha256("a"), TokenPurpose.ACTIVATION, T0, null);
        assertThatThrownBy(() -> activation.consume(T0.plus(Duration.ofHours(72)))).hasFieldOrPropertyWithValue("code", "invalid_token");
    }
}
