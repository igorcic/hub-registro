package br.com.hubregistro.integration.web;

import br.com.hubregistro.integration.application.BankIntegrationService;
import br.com.hubregistro.integration.application.RegistryIntegrationService;
import br.com.hubregistro.integration.application.RegistryIntegrationService.Configured;
import br.com.hubregistro.integration.domain.Certificate;
import br.com.hubregistro.integration.web.IntegrationDtos.BankIntegrationResponse;
import br.com.hubregistro.integration.web.IntegrationDtos.CertificateResponse;
import br.com.hubregistro.integration.web.IntegrationDtos.ConfigureBankIntegration;
import br.com.hubregistro.integration.web.IntegrationDtos.ConfigureRegistryIntegration;
import br.com.hubregistro.integration.web.IntegrationDtos.HealthCheckResponse;
import br.com.hubregistro.integration.web.IntegrationDtos.RegistryIntegrationResponse;
import br.com.hubregistro.integration.web.IntegrationDtos.StatusRequest;
import br.com.hubregistro.integration.web.IntegrationDtos.WebhookSecretResponse;
import br.com.hubregistro.security.HubSecurity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations")
class IntegrationController {

    private final RegistryIntegrationService registries;
    private final BankIntegrationService banks;

    IntegrationController(RegistryIntegrationService registries, BankIntegrationService banks) {
        this.registries = registries;
        this.banks = banks;
    }

    // ---------- registradoras ----------

    @GetMapping("/registries/{registryId}")
    @PreAuthorize("hasAuthority('integration:read')")
    RegistryIntegrationResponse get(@PathVariable UUID registryId) {
        return RegistryIntegrationResponse.of(registries.get(HubSecurity.requirePrincipal(), registryId), null);
    }

    @PutMapping("/registries/{registryId}")
    @PreAuthorize("hasAuthority('integration:manage')")
    RegistryIntegrationResponse configure(@PathVariable UUID registryId, @Valid @RequestBody ConfigureRegistryIntegration body) {
        Configured configured = registries.configure(HubSecurity.requirePrincipal(), registryId, body.toSettings());
        return RegistryIntegrationResponse.of(configured.integration(), configured.newWebhookSecret());
    }

    @PatchMapping("/registries/{registryId}/status")
    @PreAuthorize("hasAuthority('integration:manage')")
    RegistryIntegrationResponse changeStatus(@PathVariable UUID registryId, @Valid @RequestBody StatusRequest body) {
        return RegistryIntegrationResponse.of(registries.changeStatus(HubSecurity.requirePrincipal(), registryId, body.active()), null);
    }

    @PostMapping("/registries/{registryId}/connectivity-test")
    @PreAuthorize("hasAuthority('integration:manage')")
    HealthCheckResponse testConnectivity(@PathVariable UUID registryId) {
        return HealthCheckResponse.of(registries.testConnectivity(HubSecurity.requirePrincipal(), registryId));
    }

    @GetMapping("/registries/{registryId}/health-checks")
    @PreAuthorize("hasAuthority('integration:read')")
    List<HealthCheckResponse> healthChecks(@PathVariable UUID registryId, @RequestParam(defaultValue = "50") int limit) {
        return registries.healthHistory(HubSecurity.requirePrincipal(), registryId, limit).stream().map(HealthCheckResponse::of).toList();
    }

    @PostMapping("/registries/{registryId}/webhook-secret")
    @PreAuthorize("hasAuthority('integration:manage')")
    WebhookSecretResponse rotateWebhookSecret(@PathVariable UUID registryId) {
        return new WebhookSecretResponse(registries.rotateWebhookSecret(HubSecurity.requirePrincipal(), registryId));
    }

    @GetMapping("/registries/{registryId}/certificates")
    @PreAuthorize("hasAuthority('integration:read')")
    List<CertificateResponse> certificates(@PathVariable UUID registryId) {
        return registries.certificates(HubSecurity.requirePrincipal(), registryId).stream().map(CertificateResponse::of).toList();
    }

    @PostMapping(value = "/registries/{registryId}/certificates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('integration:manage')")
    CertificateResponse uploadCertificate(@PathVariable UUID registryId, @RequestPart("file") MultipartFile file,
                                          @RequestParam String alias, @RequestParam Certificate.Purpose purpose,
                                          @RequestParam(required = false) String password) throws IOException {
        return CertificateResponse.of(registries.addCertificate(HubSecurity.requirePrincipal(), registryId, alias, purpose,
                file.getBytes(), password));
    }

    @DeleteMapping("/registries/{registryId}/certificates/{certificateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('integration:manage')")
    void removeCertificate(@PathVariable UUID registryId, @PathVariable UUID certificateId) {
        registries.removeCertificate(HubSecurity.requirePrincipal(), registryId, certificateId);
    }

    // ---------- bancos ----------

    @GetMapping("/banks/{bankId}")
    @PreAuthorize("hasAuthority('integration:read')")
    BankIntegrationResponse bank(@PathVariable UUID bankId) {
        return BankIntegrationResponse.of(banks.get(HubSecurity.requirePrincipal(), bankId));
    }

    @PutMapping("/banks/{bankId}")
    @PreAuthorize("hasAuthority('integration:manage')")
    BankIntegrationResponse configureBank(@PathVariable UUID bankId, @Valid @RequestBody ConfigureBankIntegration body) {
        return BankIntegrationResponse.of(banks.configure(HubSecurity.requirePrincipal(), bankId, body.callbackUrl(),
                body.callbackAuth(), body.callbackSecret(), body.allowedIps()));
    }
}
