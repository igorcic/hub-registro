package br.com.hubregistro.auth.application;

import br.com.hubregistro.auth.domain.institution.Institution;
import br.com.hubregistro.auth.domain.institution.InstitutionRepository;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.auth.domain.user.UserRepository;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Dados do chamador para o portal: nome atual e instituição. */
@Service
public class ProfileService {

    private final UserRepository users;
    private final InstitutionRepository institutions;

    public ProfileService(UserRepository users, InstitutionRepository institutions) {
        this.users = users;
        this.institutions = institutions;
    }

    public record Profile(String fullName, Institution institution) {
    }

    @Transactional(readOnly = true)
    public Profile describe(HubPrincipal principal) {
        String fullName = principal.apiClient()
                ? principal.name()
                : users.findById(UUID.fromString(principal.subject())).map(User::fullName).orElse(principal.name());
        Institution institution = institutions.findById(principal.institutionId())
                .orElse(new Institution(principal.institutionId(), principal.institutionType(), null, null));
        return new Profile(fullName, institution);
    }
}
