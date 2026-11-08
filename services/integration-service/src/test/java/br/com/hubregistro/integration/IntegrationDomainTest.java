package br.com.hubregistro.integration;

import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.integration.domain.AuthType;
import br.com.hubregistro.integration.domain.BankIntegration;
import br.com.hubregistro.integration.domain.Endpoint;
import br.com.hubregistro.integration.domain.HealthCheckResult;
import br.com.hubregistro.integration.domain.HealthStatus;
import br.com.hubregistro.integration.domain.Operation;
import br.com.hubregistro.integration.domain.RegistryIntegration;
import br.com.hubregistro.integration.domain.RegistryIntegration.Settings;
import br.com.hubregistro.integration.domain.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationDomainTest {

    private static Settings settings(String url, AuthType auth, Map<String, String> credentials) {
        return new Settings("GENERIC_REST", url, auth, credentials, 5_000, 30_000, null, null, 50,
                Map.of(Operation.SUBMIT_CONTRACT, new Endpoint("post", "/contracts")));
    }

    @Test
    void externalRegistriesRequireHttps() {
        assertThatThrownBy(() -> RegistryIntegration.create(UUID.randomUUID(),
                settings("http://api.registradora.com.br", AuthType.NONE, Map.of()), "s"))
                .hasFieldOrPropertyWithValue("code", "insecure_base_url");
        assertThat(RegistryIntegration.create(UUID.randomUUID(),
                settings("http://registry-simulator:8091/", AuthType.NONE, Map.of()), "s").baseUrl())
                .isEqualTo("http://registry-simulator:8091");
        assertThatThrownBy(() -> RegistryIntegration.create(UUID.randomUUID(),
                settings("https://user:pwd@api.reg.com.br", AuthType.NONE, Map.of()), "s"))
                .hasFieldOrPropertyWithValue("code", "invalid_base_url");
    }

    @Test
    void credentialsAreRequiredPerAuthTypeAndSecretsAreKeptWhenOmitted() {
        assertThatThrownBy(() -> RegistryIntegration.create(UUID.randomUUID(),
                settings("https://api.reg.com.br", AuthType.BASIC, Map.of("username", "hub")), "s"))
                .hasFieldOrPropertyWithValue("code", "missing_credentials");

        RegistryIntegration integration = RegistryIntegration.create(UUID.randomUUID(),
                settings("https://api.reg.com.br", AuthType.BASIC, Map.of("username", "hub", "password", "segredo", "extra", "x")), "s");
        assertThat(integration.credentials()).doesNotContainKey("extra");
        assertThat(integration.publicCredentials()).containsOnlyKeys("username");
        assertThat(integration.configuredSecretKeys()).containsExactly("password");

        integration.reconfigure(settings("https://api2.reg.com.br", AuthType.BASIC, Map.of("username", "hub2")));
        assertThat(integration.credentials()).containsEntry("password", "segredo").containsEntry("username", "hub2");

        assertThatThrownBy(() -> integration.reconfigure(settings("https://api2.reg.com.br", AuthType.API_KEY, Map.of("headerName", "X-Key"))))
                .as("ao trocar o tipo, segredos antigos não são reaproveitados")
                .hasFieldOrPropertyWithValue("code", "missing_credentials");
    }

    @Test
    void activationRequiresTheSubmitEndpoint() {
        RegistryIntegration integration = RegistryIntegration.create(UUID.randomUUID(),
                new Settings("GENERIC_REST", "https://api.reg.com.br", AuthType.NONE, Map.of(), 5_000, 30_000, null, null, 50, Map.of()), "s");

        assertThatThrownBy(integration::activate).hasFieldOrPropertyWithValue("code", "endpoint_required");
    }

    @Test
    void healthChangesAreReportedOnlyWhenTheStatusChanges() {
        RegistryIntegration integration = RegistryIntegration.create(UUID.randomUUID(),
                settings("https://api.reg.com.br", AuthType.NONE, Map.of()), "s");

        assertThat(integration.recordHealth(HealthStatus.UP, Instant.now())).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(integration.recordHealth(HealthStatus.UP, Instant.now())).isNull();
        assertThat(integration.recordHealth(HealthStatus.DOWN, Instant.now())).isEqualTo(HealthStatus.UP);
    }

    @Test
    void slowSuccessfulResponsesCountAsDegraded() {
        assertThat(HealthCheckResult.fromResponse(200, 150, Instant.now()).status()).isEqualTo(HealthStatus.UP);
        assertThat(HealthCheckResult.fromResponse(200, 2_500, Instant.now()).status()).isEqualTo(HealthStatus.DEGRADED);
        assertThat(HealthCheckResult.fromResponse(503, 50, Instant.now()).status()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void retryDelaysGrowExponentiallyUpToTheMaximum() {
        RetryPolicy policy = new RetryPolicy(5, Duration.ofSeconds(10), 2.0, Duration.ofMinutes(1));

        assertThat(policy.delayBefore(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.delayBefore(3)).isEqualTo(Duration.ofSeconds(40));
        assertThat(policy.delayBefore(5)).isEqualTo(Duration.ofMinutes(1));
        assertThatThrownBy(() -> new RetryPolicy(5, Duration.ofMinutes(2), 2.0, Duration.ofMinutes(1)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void endpointsMustBeRelativePaths() {
        assertThatThrownBy(() -> new Endpoint("GET", "https://outro-host/x")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Endpoint("GET", "/../admin")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Endpoint("TRACE", "/x")).isInstanceOf(DomainException.class);
    }

    @Test
    void bankCallbackValidatesIpsAndRequiresSecretForSignedCallbacks() {
        assertThatThrownBy(() -> BankIntegration.create(UUID.randomUUID(), "https://erp.banco.com.br/callback",
                BankIntegration.CallbackAuth.HMAC, null, List.of())).hasFieldOrPropertyWithValue("code", "missing_credentials");
        assertThatThrownBy(() -> BankIntegration.create(UUID.randomUUID(), null, BankIntegration.CallbackAuth.NONE, null,
                List.of("10.0.0.0/33"))).hasFieldOrPropertyWithValue("code", "invalid_ip");
        assertThatThrownBy(() -> BankIntegration.create(UUID.randomUUID(), null, BankIntegration.CallbackAuth.NONE, null,
                List.of("exemplo.com"))).hasFieldOrPropertyWithValue("code", "invalid_ip");

        BankIntegration ok = BankIntegration.create(UUID.randomUUID(), "https://erp.banco.com.br/callback",
                BankIntegration.CallbackAuth.HMAC, "segredo", List.of("200.10.20.0/24", "2001:db8::1"));
        assertThat(ok.active()).isTrue();
        assertThat(ok.allowedIps()).hasSize(2);
    }
}
