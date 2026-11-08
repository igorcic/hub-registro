package br.com.hubregistro.integration.domain;

import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.id.UuidV7;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Integração do lado do banco: para onde o Hub envia as mudanças de status (callback) e de quais IPs o banco
 * pode chamar a API com as suas credenciais.
 */
public class BankIntegration {

    public enum CallbackAuth { NONE, HMAC, API_KEY }

    private final UUID id;
    private final UUID bankId;
    private String callbackUrl;
    private CallbackAuth callbackAuth;
    private String callbackSecret;
    private List<String> allowedIps;
    private boolean active;

    private BankIntegration(UUID id, UUID bankId) {
        this.id = Objects.requireNonNull(id);
        this.bankId = Objects.requireNonNull(bankId);
    }

    public static BankIntegration create(UUID bankId, String callbackUrl, CallbackAuth auth, String secret, List<String> allowedIps) {
        BankIntegration integration = new BankIntegration(UuidV7.generate(), bankId);
        integration.configure(callbackUrl, auth, secret, allowedIps);
        return integration;
    }

    public static BankIntegration restore(UUID id, UUID bankId, String callbackUrl, CallbackAuth auth, String secret,
                                          List<String> allowedIps, boolean active) {
        BankIntegration integration = new BankIntegration(id, bankId);
        integration.callbackUrl = callbackUrl;
        integration.callbackAuth = auth;
        integration.callbackSecret = secret;
        integration.allowedIps = List.copyOf(allowedIps);
        integration.active = active;
        return integration;
    }

    /** {@code secret} nulo mantém o atual (quando o tipo de autenticação não muda). */
    public void configure(String callbackUrl, CallbackAuth auth, String secret, List<String> allowedIps) {
        this.callbackUrl = callbackUrl == null || callbackUrl.isBlank() ? null : RegistryIntegration.validateBaseUrl(callbackUrl);
        CallbackAuth previous = this.callbackAuth;
        this.callbackAuth = Objects.requireNonNull(auth, "Autenticação do callback obrigatória");
        if (secret != null && !secret.isBlank()) {
            this.callbackSecret = secret;
        } else if (previous != auth) {
            this.callbackSecret = null;
        }
        if (auth != CallbackAuth.NONE && this.callbackSecret == null) {
            throw new DomainException("missing_credentials", "Callback com " + auth + " exige um segredo");
        }
        if (auth == CallbackAuth.NONE) {
            this.callbackSecret = null;
        }
        this.allowedIps = validateIps(allowedIps);
        this.active = this.callbackUrl != null;
    }

    static List<String> validateIps(List<String> ips) {
        if (ips == null) {
            return List.of();
        }
        if (ips.size() > 50) {
            throw new DomainException("invalid_ip", "No máximo 50 IPs ou faixas");
        }
        return ips.stream().map(String::trim).peek(BankIntegration::validateIp).distinct().toList();
    }

    private static void validateIp(String value) {
        String[] parts = value.split("/", 2);
        if (!parts[0].matches("^[0-9a-fA-F:.]+$")) {
            throw new DomainException("invalid_ip", "IP inválido: " + value);
        }
        try {
            InetAddress address = InetAddress.getByName(parts[0]);
            if (parts.length == 2) {
                int prefix = Integer.parseInt(parts[1]);
                int max = address.getAddress().length * 8;
                if (prefix < 0 || prefix > max) {
                    throw new DomainException("invalid_ip", "Prefixo inválido: " + value);
                }
            }
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("invalid_ip", "IP inválido: " + value);
        }
    }

    public UUID id() { return id; }
    public UUID bankId() { return bankId; }
    public String callbackUrl() { return callbackUrl; }
    public CallbackAuth callbackAuth() { return callbackAuth; }
    public String callbackSecret() { return callbackSecret; }
    public List<String> allowedIps() { return allowedIps; }
    public boolean active() { return active; }
    public boolean hasCallbackSecret() { return callbackSecret != null; }

    static URI uri(String url) {
        return URI.create(url);
    }
}
