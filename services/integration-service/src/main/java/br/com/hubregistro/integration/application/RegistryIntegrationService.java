package br.com.hubregistro.integration.application;

import br.com.hubregistro.commons.crypto.SecureTokens;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.error.NotFoundException;
import br.com.hubregistro.commons.tenant.InstitutionType;
import br.com.hubregistro.integration.application.CertificateReader.ParsedCertificate;
import br.com.hubregistro.integration.domain.AuthType;
import br.com.hubregistro.integration.domain.Certificate;
import br.com.hubregistro.integration.domain.HealthCheckResult;
import br.com.hubregistro.integration.domain.HealthStatus;
import br.com.hubregistro.integration.domain.IntegrationRepository;
import br.com.hubregistro.integration.domain.RegistryIntegration;
import br.com.hubregistro.integration.domain.RegistryIntegration.Settings;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class RegistryIntegrationService {

    private final IntegrationRepository integrations;
    private final HealthProbe probe;
    private final CertificateReader certificateReader;
    private final IntegrationEventPublisher events;
    private final TransactionTemplate transaction;

    public RegistryIntegrationService(IntegrationRepository integrations, HealthProbe probe, CertificateReader certificateReader,
                                      IntegrationEventPublisher events, TransactionTemplate transaction) {
        this.integrations = integrations;
        this.probe = probe;
        this.certificateReader = certificateReader;
        this.events = events;
        this.transaction = transaction;
    }

    /** Resultado da configuração; o segredo do webhook só aparece na criação ou na rotação. */
    public record Configured(RegistryIntegration integration, String newWebhookSecret) {
    }

    @Transactional(readOnly = true)
    public RegistryIntegration get(HubPrincipal actor, UUID registryId) {
        IntegrationAccess.requireVisible(actor, InstitutionType.REGISTRY, registryId);
        return integrations.findByRegistry(registryId).orElseThrow(() -> new NotFoundException("Integração"));
    }

    @Transactional
    public Configured configure(HubPrincipal actor, UUID registryId, Settings settings) {
        IntegrationAccess.requireManage(actor, InstitutionType.REGISTRY, registryId);
        if (settings.authType() == AuthType.MTLS) {
            requireClientCertificateWhenExisting(registryId, settings);
        }
        var existing = integrations.findByRegistry(registryId);
        String newSecret = null;
        RegistryIntegration integration;
        if (existing.isPresent()) {
            integration = existing.get();
            integration.reconfigure(settings);
        } else {
            newSecret = SecureTokens.generate(32);
            integration = RegistryIntegration.create(registryId, settings, newSecret);
        }
        integrations.save(integration);
        events.configured(actor, integration);
        return new Configured(integration, newSecret);
    }

    @Transactional
    public RegistryIntegration changeStatus(HubPrincipal actor, UUID registryId, boolean active) {
        IntegrationAccess.requireManage(actor, InstitutionType.REGISTRY, registryId);
        RegistryIntegration integration = integrations.findByRegistry(registryId).orElseThrow(() -> new NotFoundException("Integração"));
        if (active) {
            if (integration.authType() == AuthType.MTLS) {
                String alias = integration.credentials().get("certificateAlias");
                integrations.activeCertificate(integration.id(), alias, Certificate.Purpose.CLIENT_AUTH)
                        .orElseThrow(() -> new DomainException("certificate_required",
                                "Cadastre o certificado de cliente '" + alias + "' antes de ativar"));
            }
            integration.activate();
        } else {
            HealthStatus previous = integration.healthStatus();
            integration.deactivate();
            if (previous != HealthStatus.UNKNOWN) {
                events.healthChanged(integration, previous,
                        new HealthCheckResult(HealthStatus.UNKNOWN, null, null, "Integração desativada", java.time.Instant.now()));
            }
        }
        integrations.save(integration);
        events.statusChanged(actor, integration);
        return integration;
    }

    @Transactional
    public String rotateWebhookSecret(HubPrincipal actor, UUID registryId) {
        IntegrationAccess.requireManage(actor, InstitutionType.REGISTRY, registryId);
        RegistryIntegration integration = integrations.findByRegistry(registryId).orElseThrow(() -> new NotFoundException("Integração"));
        String secret = SecureTokens.generate(32);
        integration.rotateWebhookSecret(secret);
        integrations.save(integration);
        return secret;
    }

    /**
     * Teste de conectividade sob demanda. A chamada HTTP acontece fora de transação; o resultado é gravado no
     * histórico e atualiza a saúde (com evento, se mudou) só para integrações ativas.
     */
    public HealthCheckResult testConnectivity(HubPrincipal actor, UUID registryId) {
        RegistryIntegration integration = transaction.execute(status -> {
            IntegrationAccess.requireManage(actor, InstitutionType.REGISTRY, registryId);
            return integrations.findByRegistry(registryId).orElseThrow(() -> new NotFoundException("Integração"));
        });
        HealthCheckResult result = probe.check(integration);
        transaction.executeWithoutResult(status -> HealthMonitor.record(integrations, events, integration.id(), result));
        return result;
    }

    @Transactional(readOnly = true)
    public List<HealthCheckResult> healthHistory(HubPrincipal actor, UUID registryId, int limit) {
        RegistryIntegration integration = get(actor, registryId);
        return integrations.recentHealthChecks(integration.id(), Math.clamp(limit, 1, 200));
    }

    // ---------- certificados ----------

    @Transactional
    public Certificate addCertificate(HubPrincipal actor, UUID registryId, String alias, Certificate.Purpose purpose,
                                      byte[] content, String password) {
        IntegrationAccess.requireManage(actor, InstitutionType.REGISTRY, registryId);
        RegistryIntegration integration = integrations.findByRegistry(registryId)
                .orElseThrow(() -> new DomainException("integration_required", "Configure a integração antes de enviar certificados"));
        boolean aliasInUse = integrations.certificates(integration.id()).stream()
                .anyMatch(c -> c.active() && c.alias().equals(alias) && c.purpose() == purpose);
        if (aliasInUse) {
            throw new DomainException("alias_in_use", "Já existe um certificado ativo com este alias e finalidade; remova-o antes");
        }
        ParsedCertificate parsed = certificateReader.read(integration.id(), alias, purpose, content, password);
        if (parsed.certificate().isExpired(java.time.Instant.now())) {
            throw new DomainException("certificate_expired", "Certificado vencido em " + parsed.certificate().notAfter());
        }
        integrations.addCertificate(parsed.certificate(), parsed.keystoreBytes(), parsed.keystorePassword());
        return parsed.certificate();
    }

    @Transactional(readOnly = true)
    public List<Certificate> certificates(HubPrincipal actor, UUID registryId) {
        RegistryIntegration integration = get(actor, registryId);
        return integrations.certificates(integration.id());
    }

    @Transactional
    public void removeCertificate(HubPrincipal actor, UUID registryId, UUID certificateId) {
        IntegrationAccess.requireManage(actor, InstitutionType.REGISTRY, registryId);
        RegistryIntegration integration = integrations.findByRegistry(registryId).orElseThrow(() -> new NotFoundException("Integração"));
        if (!integrations.removeCertificate(integration.id(), certificateId)) {
            throw new NotFoundException("Certificado");
        }
    }

    private void requireClientCertificateWhenExisting(UUID registryId, Settings settings) {
        // Na primeira configuração o certificado ainda não pode ter sido enviado; a exigência vale na ativação.
        if (settings.credentials() == null || settings.credentials().get("certificateAlias") == null) {
            throw new DomainException("missing_credentials", "Informe certificateAlias para MTLS");
        }
    }
}
