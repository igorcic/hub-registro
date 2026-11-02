package br.com.hubregistro.auth.infrastructure.config;

import br.com.hubregistro.auth.application.BootstrapAdminService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Ambientes novos (fora do dev) ganham o primeiro SUPER_ADMIN a partir de HUB_AUTH_BOOTSTRAP_ADMIN_*. */
@Component
class BootstrapAdminRunner implements ApplicationRunner {

    private final BootstrapAdminService bootstrap;
    private final AuthProperties properties;

    BootstrapAdminRunner(BootstrapAdminService bootstrap, AuthProperties properties) {
        this.bootstrap = bootstrap;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = properties.getBootstrapAdmin();
        if (admin.getEmail() != null && !admin.getEmail().isBlank() && admin.getPassword() != null) {
            bootstrap.ensureSuperAdmin(admin.getEmail(), admin.getName(), admin.getPassword());
        }
    }
}
