package br.com.hubregistro.integration.application;

import br.com.hubregistro.integration.domain.HealthCheckResult;
import br.com.hubregistro.integration.domain.RegistryIntegration;

/** Faz uma chamada real à registradora (endpoint HEALTH_CHECK ou URL base) e classifica o resultado. */
public interface HealthProbe {

    HealthCheckResult check(RegistryIntegration integration);
}
