package br.com.hubregistro.integration.application;

import br.com.hubregistro.integration.domain.HealthCheckResult;
import br.com.hubregistro.integration.domain.HealthStatus;
import br.com.hubregistro.integration.domain.IntegrationRepository;
import br.com.hubregistro.integration.domain.RegistryIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Monitoramento periódico da disponibilidade das integrações ativas.
 * Cada rodada reserva as integrações vencidas (SKIP LOCKED: cada uma por uma só instância), chama a
 * registradora fora de transação e grava o resultado. Mudança de UP/DEGRADED/DOWN gera evento.
 */
@Service
public class HealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(HealthMonitor.class);
    private static final int BATCH = 20;

    private final IntegrationRepository integrations;
    private final HealthProbe probe;
    private final IntegrationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Duration interval;

    public HealthMonitor(IntegrationRepository integrations, HealthProbe probe, IntegrationEventPublisher events,
                         TransactionTemplate transaction,
                         @Value("${hub.integration.health-check.interval:60s}") Duration interval) {
        this.integrations = integrations;
        this.probe = probe;
        this.events = events;
        this.transaction = transaction;
        this.interval = interval;
    }

    @Scheduled(fixedDelayString = "${hub.integration.health-check.poll:15s}", initialDelayString = "${hub.integration.health-check.initial-delay:20s}")
    public void runDueChecks() {
        List<UUID> due;
        do {
            due = transaction.execute(status -> integrations.claimDueForHealthCheck(interval, BATCH));
            for (UUID id : due) {
                try {
                    checkOne(id);
                } catch (RuntimeException e) {
                    log.error("Falha ao verificar a integração {}", id, e);
                }
            }
        } while (due != null && due.size() == BATCH);
    }

    void checkOne(UUID integrationId) {
        RegistryIntegration integration = transaction.execute(status -> integrations.findById(integrationId).orElse(null));
        if (integration == null || !integration.active()) {
            return;
        }
        HealthCheckResult result = probe.check(integration);
        transaction.executeWithoutResult(status -> record(integrations, events, integrationId, result));
    }

    /** Grava a verificação; atualiza a saúde e publica evento se mudou (só para integrações ativas). */
    static void record(IntegrationRepository integrations, IntegrationEventPublisher events, UUID integrationId,
                       HealthCheckResult result) {
        integrations.appendHealthCheck(integrationId, result);
        RegistryIntegration current = integrations.findById(integrationId).orElse(null);
        if (current == null || !current.active()) {
            return;
        }
        HealthStatus previous = current.recordHealth(result.status(), result.checkedAt());
        integrations.save(current);
        if (previous != null) {
            events.healthChanged(current, previous, result);
            log.info("Integração da registradora {}: {} -> {}", current.registryId(), previous, result.status());
        }
    }
}
