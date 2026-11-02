package br.com.hubregistro.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "hub.security")
public class HubSecurityProperties {

    /** Caminhos acessíveis sem token, além de health/info/prometheus (padrões Ant). */
    private List<String> publicPaths = new ArrayList<>();

    private final Rls rls = new Rls();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public Rls getRls() {
        return rls;
    }

    public static class Rls {

        /** Aplica app.tenant_type/app.tenant_id em cada conexão (serviços com Row-Level Security). */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
