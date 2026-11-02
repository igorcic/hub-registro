package br.com.hubregistro.auth.application;

import br.com.hubregistro.auth.application.port.PasswordHasher;
import br.com.hubregistro.auth.domain.institution.InstitutionRepository;
import br.com.hubregistro.auth.domain.user.Email;
import br.com.hubregistro.auth.domain.user.PasswordPolicy;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.auth.domain.user.UserRepository;
import br.com.hubregistro.commons.tenant.InstitutionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;

/** Cria o primeiro SUPER_ADMIN quando ainda não existe nenhum. Sem efeito nas inicializações seguintes. */
@Service
public class BootstrapAdminService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);

    private final UserRepository users;
    private final PermissionResolver permissions;
    private final PasswordHasher hasher;
    private final Clock clock;

    public BootstrapAdminService(UserRepository users, PermissionResolver permissions, PasswordHasher hasher, Clock clock) {
        this.users = users;
        this.permissions = permissions;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional
    public void ensureSuperAdmin(String rawEmail, String name, String password) {
        if (users.existsWithRole("SUPER_ADMIN")) {
            return;
        }
        Email email = new Email(rawEmail);
        PasswordPolicy.validate(password, email);
        User admin = User.create(InstitutionRepository.HUB_INSTITUTION_ID, InstitutionType.HUB, name, email,
                permissions.requireRoles(Set.of("SUPER_ADMIN")), hasher.hash(password));
        admin.setPassword(hasher.hash(password), clock.instant());
        users.save(admin);
        log.info("Primeiro SUPER_ADMIN criado: {}", email.value());
    }
}
