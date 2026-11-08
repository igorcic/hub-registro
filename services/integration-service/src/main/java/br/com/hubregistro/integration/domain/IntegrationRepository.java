package br.com.hubregistro.integration.domain;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationRepository {

    Optional<RegistryIntegration> findByRegistry(UUID registryId);

    Optional<RegistryIntegration> findById(UUID id);

    void save(RegistryIntegration integration);

    /**
     * Reserva até {@code limit} integrações ativas cuja última verificação é mais antiga que {@code interval},
     * marcando-as como verificadas agora. Com várias instâncias, cada integração é reservada por uma só.
     */
    List<UUID> claimDueForHealthCheck(Duration interval, int limit);

    void appendHealthCheck(UUID integrationId, HealthCheckResult result);

    List<HealthCheckResult> recentHealthChecks(UUID integrationId, int limit);

    // ----- certificados -----

    void addCertificate(Certificate certificate, byte[] keystoreBytes, String keystorePassword);

    List<Certificate> certificates(UUID integrationId);

    Optional<StoredKeystore> activeCertificate(UUID integrationId, String alias, Certificate.Purpose purpose);

    boolean removeCertificate(UUID integrationId, UUID certificateId);

    record StoredKeystore(Certificate certificate, byte[] keystoreBytes, String password) {
    }

    // ----- integração dos bancos -----

    Optional<BankIntegration> findBankIntegration(UUID bankId);

    void save(BankIntegration integration);
}
