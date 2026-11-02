package br.com.hubregistro.auth.domain;

import br.com.hubregistro.auth.domain.role.Role;
import br.com.hubregistro.auth.domain.user.Email;
import br.com.hubregistro.auth.domain.user.LoginPolicy;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.auth.domain.user.UserStatus;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.tenant.InstitutionType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Role BANK_ADMIN = new Role("BANK_ADMIN", "Admin", "-", InstitutionType.BANK, Set.of("user:manage"));
    private static final Role SUPER_ADMIN = new Role("SUPER_ADMIN", "Super", "-", InstitutionType.HUB, Set.of("user:manage-any"));
    private static final Instant T0 = Instant.parse("2026-11-02T12:00:00Z");
    private static final LoginPolicy POLICY = new LoginPolicy(5, Duration.ofMinutes(15));

    private User newBankUser() {
        return User.create(UUID.randomUUID(), InstitutionType.BANK, "Maria Silva", new Email("maria@banco.com"),
                Set.of(BANK_ADMIN), "unusable-hash");
    }

    private User activeBankUser() {
        User user = newBankUser();
        user.setPassword("hash", T0);
        return user;
    }

    @Test
    void newUserWaitsForActivationAndCannotLogIn() {
        User user = newBankUser();

        assertThat(user.status()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(user.canAuthenticate(T0)).isFalse();
    }

    @Test
    void settingThePasswordActivatesTheAccount() {
        User user = newBankUser();

        user.setPassword("new-hash", T0);

        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.canAuthenticate(T0)).isTrue();
        assertThat(user.passwordChangedAt()).isEqualTo(T0);
    }

    @Test
    void adminCannotActivateWithoutTheUserSettingAPassword() {
        assertThatThrownBy(() -> newBankUser().changeStatus(UserStatus.ACTIVE))
                .isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("code", "activation_required");
    }

    @Test
    void rolesMustMatchTheInstitutionType() {
        User user = newBankUser();

        assertThatThrownBy(() -> user.replaceRoles(Set.of(SUPER_ADMIN), InstitutionType.BANK))
                .isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("code", "role_not_allowed");
        assertThatThrownBy(() -> user.replaceRoles(Set.of(), InstitutionType.BANK))
                .isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("code", "roles_required");
    }

    @Test
    void fifthWrongPasswordLocksForFifteenMinutes() {
        User user = activeBankUser();

        for (int i = 1; i <= 4; i++) {
            assertThat(user.loginFailed(T0.plusSeconds(i), POLICY)).isFalse();
        }
        Instant fifth = T0.plusSeconds(5);
        assertThat(user.loginFailed(fifth, POLICY)).isTrue();

        assertThat(user.canAuthenticate(fifth.plus(Duration.ofMinutes(14)))).isFalse();
        assertThat(user.canAuthenticate(fifth.plus(Duration.ofMinutes(15)))).isTrue();
    }

    @Test
    void failuresDuringTheLockDoNotExtendIt() {
        User user = activeBankUser();
        for (int i = 0; i < 5; i++) {
            user.loginFailed(T0, POLICY);
        }
        Instant lockedUntil = user.lockedUntil();

        assertThat(user.loginFailed(T0.plusSeconds(60), POLICY)).isFalse();
        assertThat(user.lockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    void successfulLoginResetsTheFailureCounter() {
        User user = activeBankUser();
        user.loginFailed(T0, POLICY);
        user.loginFailed(T0, POLICY);

        user.loginSucceeded(T0.plusSeconds(10));

        assertThat(user.failedLoginAttempts()).isZero();
        assertThat(user.lastLoginAt()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    void disabledUserCannotAuthenticateNorResetPassword() {
        User user = activeBankUser();

        user.changeStatus(UserStatus.DISABLED);

        assertThat(user.canAuthenticate(T0)).isFalse();
        assertThatThrownBy(() -> user.setPassword("h", T0)).hasFieldOrPropertyWithValue("code", "user_disabled");
    }
}
