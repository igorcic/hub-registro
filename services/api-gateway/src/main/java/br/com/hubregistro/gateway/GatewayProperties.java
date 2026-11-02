package br.com.hubregistro.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "hub.gateway")
public class GatewayProperties {

    /** Caminhos sem token (login, renovação, redefinição de senha, token OAuth, webhooks assinados). */
    private List<String> publicPaths = List.of();

    /** Origens do portal autorizadas a chamar a API pelo navegador. */
    private List<String> corsAllowedOrigins = List.of("http://localhost:4200");

    private final RateLimit rateLimit = new RateLimit();

    public static class RateLimit {
        private boolean enabled = true;
        /** Requisições por minuto por usuário do portal. */
        private int userPerMinute = 300;
        /** Requisições por minuto por IP em caminhos públicos (inclui tentativas de login). */
        private int anonymousPerMinute = 30;
        /** Para clientes de API vale o limite da credencial (claim "rl"); este é o padrão se faltar. */
        private int clientDefaultPerMinute = 600;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getUserPerMinute() { return userPerMinute; }
        public void setUserPerMinute(int userPerMinute) { this.userPerMinute = userPerMinute; }
        public int getAnonymousPerMinute() { return anonymousPerMinute; }
        public void setAnonymousPerMinute(int anonymousPerMinute) { this.anonymousPerMinute = anonymousPerMinute; }
        public int getClientDefaultPerMinute() { return clientDefaultPerMinute; }
        public void setClientDefaultPerMinute(int clientDefaultPerMinute) { this.clientDefaultPerMinute = clientDefaultPerMinute; }
    }

    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) { this.publicPaths = publicPaths; }
    public List<String> getCorsAllowedOrigins() { return corsAllowedOrigins; }
    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) { this.corsAllowedOrigins = corsAllowedOrigins; }
    public RateLimit getRateLimit() { return rateLimit; }
}
