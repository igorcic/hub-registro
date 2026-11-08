package br.com.hubregistro.integration.application;

import br.com.hubregistro.commons.error.NotFoundException;
import br.com.hubregistro.commons.tenant.InstitutionType;
import br.com.hubregistro.integration.domain.BankIntegration;
import br.com.hubregistro.integration.domain.IntegrationRepository;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Callback de retorno ao banco e IPs autorizados; o administrador do banco configura o do próprio banco. */
@Service
public class BankIntegrationService {

    private final IntegrationRepository integrations;

    public BankIntegrationService(IntegrationRepository integrations) {
        this.integrations = integrations;
    }

    @Transactional(readOnly = true)
    public BankIntegration get(HubPrincipal actor, UUID bankId) {
        IntegrationAccess.requireVisible(actor, InstitutionType.BANK, bankId);
        return integrations.findBankIntegration(bankId).orElseThrow(() -> new NotFoundException("Integração"));
    }

    @Transactional
    public BankIntegration configure(HubPrincipal actor, UUID bankId, String callbackUrl, BankIntegration.CallbackAuth auth,
                                     String secret, List<String> allowedIps) {
        IntegrationAccess.requireManage(actor, InstitutionType.BANK, bankId);
        BankIntegration integration = integrations.findBankIntegration(bankId)
                .map(existing -> { existing.configure(callbackUrl, auth, secret, allowedIps); return existing; })
                .orElseGet(() -> BankIntegration.create(bankId, callbackUrl, auth, secret, allowedIps));
        integrations.save(integration);
        return integration;
    }
}
