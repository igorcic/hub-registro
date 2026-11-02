package br.com.hubregistro.auth.domain;

import br.com.hubregistro.auth.domain.session.RefreshToken;
import br.com.hubregistro.auth.domain.session.SessionPolicy;
import br.com.hubregistro.commons.crypto.SecureTokens;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenTest {

    private static final Instant T0 = Instant.parse("2026-11-02T12:00:00Z");
    private static final SessionPolicy POLICY = new SessionPolicy(Duration.ofMinutes(15), Duration.ofHours(8), Duration.ofHours(12));

    private RefreshToken login() {
        return RefreshToken.startSession(UUID.randomUUID(), SecureTokens.sha256("t1"), T0, POLICY, "10.0.0.1", "browser");
    }

    @Test
    void sessionStartsWithEightHourTokenAndTwelveHourCeiling() {
        RefreshToken token = login();

        assertThat(token.expiresAt()).isEqualTo(T0.plus(Duration.ofHours(8)));
        assertThat(token.sessionExpiresAt()).isEqualTo(T0.plus(Duration.ofHours(12)));
        assertThat(token.isActive(T0)).isTrue();
    }

    @Test
    void rotationKeepsTheFamilyAndRetiresThePreviousToken() {
        RefreshToken first = login();
        Instant later = T0.plus(Duration.ofHours(1));

        RefreshToken second = first.rotate(SecureTokens.sha256("t2"), later, POLICY, null, null);

        assertThat(second.familyId()).isEqualTo(first.familyId());
        assertThat(first.wasRotated()).isTrue();
        assertThat(first.replacedBy()).isEqualTo(second.id());
        assertThat(first.isActive(later)).isFalse();
        assertThat(second.isActive(later)).isTrue();
    }

    @Test
    void rotationNeverExtendsTheSessionCeiling() {
        RefreshToken first = login();

        RefreshToken second = first.rotate(SecureTokens.sha256("t2"), T0.plus(Duration.ofHours(7)), POLICY, null, null);

        assertThat(second.expiresAt()).isEqualTo(T0.plus(Duration.ofHours(12)));
    }

    @Test
    void aRetiredTokenCannotBeRotatedAgain() {
        RefreshToken first = login();
        first.rotate(SecureTokens.sha256("t2"), T0.plusSeconds(1), POLICY, null, null);

        assertThatThrownBy(() -> first.rotate(SecureTokens.sha256("t3"), T0.plusSeconds(2), POLICY, null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
