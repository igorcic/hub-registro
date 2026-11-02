package br.com.hubregistro.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "hub.auth")
public class AuthProperties {

    /** Chave AES-256 em Base64 que cifra as chaves privadas de assinatura no banco. Obrigatória. */
    private String encryptionKey;

    /** Endereço do portal, usado nos links de ativação e redefinição enviados por e-mail. */
    private String portalUrl = "http://localhost:4200";

    private String mailFrom = "Hub de Registro <nao-responda@hub-registro.local>";

    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofHours(8);
    private Duration maxSession = Duration.ofHours(12);
    private int maxFailedAttempts = 5;
    private Duration lockDuration = Duration.ofMinutes(15);

    private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

    public static class BootstrapAdmin {
        /** Cria o primeiro SUPER_ADMIN na inicialização, se ainda não houver nenhum. */
        private String email;
        private String password;
        private String name = "Administrador do Hub";

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
    public String getPortalUrl() { return portalUrl; }
    public void setPortalUrl(String portalUrl) { this.portalUrl = portalUrl; }
    public String getMailFrom() { return mailFrom; }
    public void setMailFrom(String mailFrom) { this.mailFrom = mailFrom; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    public Duration getMaxSession() { return maxSession; }
    public void setMaxSession(Duration maxSession) { this.maxSession = maxSession; }
    public int getMaxFailedAttempts() { return maxFailedAttempts; }
    public void setMaxFailedAttempts(int maxFailedAttempts) { this.maxFailedAttempts = maxFailedAttempts; }
    public Duration getLockDuration() { return lockDuration; }
    public void setLockDuration(Duration lockDuration) { this.lockDuration = lockDuration; }
    public BootstrapAdmin getBootstrapAdmin() { return bootstrapAdmin; }
}
