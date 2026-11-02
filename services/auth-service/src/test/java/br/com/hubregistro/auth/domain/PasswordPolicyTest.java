package br.com.hubregistro.auth.domain;

import br.com.hubregistro.auth.domain.AuthErrors.WeakPasswordException;
import br.com.hubregistro.auth.domain.user.Email;
import br.com.hubregistro.auth.domain.user.PasswordPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static final Email EMAIL = new Email("maria.silva@banco.com");

    @ParameterizedTest
    @ValueSource(strings = {"Curta@1", "password1234", "PASSWORD1234", "aaaaaaaabbbb", "maria.silva-2026"})
    void rejectsWeakPasswords(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password, EMAIL)).isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void rejectsPasswordsLongerThan128Characters() {
        assertThatThrownBy(() -> PasswordPolicy.validate("ab1".repeat(43), EMAIL)).isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void acceptsLongPassphrasesWithoutCompositionRules() {
        assertThatCode(() -> PasswordPolicy.validate("cavalo correto bateria grampo", EMAIL)).doesNotThrowAnyException();
    }
}
