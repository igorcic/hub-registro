package br.com.hubregistro.auth.application;

import br.com.hubregistro.auth.application.port.AccountMailer;
import br.com.hubregistro.auth.application.port.AuthEventPublisher;
import br.com.hubregistro.auth.application.port.PasswordHasher;
import br.com.hubregistro.auth.domain.AuthErrors.InvalidCredentialsException;
import br.com.hubregistro.auth.domain.AuthErrors.InvalidTokenException;
import br.com.hubregistro.auth.domain.reset.PasswordToken;
import br.com.hubregistro.auth.domain.reset.PasswordTokenRepository;
import br.com.hubregistro.auth.domain.reset.TokenPurpose;
import br.com.hubregistro.auth.domain.session.RefreshToken;
import br.com.hubregistro.auth.domain.session.RefreshTokenRepository;
import br.com.hubregistro.auth.domain.user.Email;
import br.com.hubregistro.auth.domain.user.PasswordPolicy;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.auth.domain.user.UserRepository;
import br.com.hubregistro.auth.domain.user.UserStatus;
import br.com.hubregistro.commons.crypto.SecureTokens;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Ativação de conta, redefinição de senha por e-mail e troca de senha pelo próprio usuário. */
@Service
public class PasswordService {

    private static final int LINK_TOKEN_BYTES = 32;

    private final UserRepository users;
    private final PasswordTokenRepository passwordTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher hasher;
    private final AccountMailer mailer;
    private final AuthEventPublisher events;
    private final Clock clock;

    public PasswordService(UserRepository users, PasswordTokenRepository passwordTokens,
                           RefreshTokenRepository refreshTokens, PasswordHasher hasher, AccountMailer mailer,
                           AuthEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordTokens = passwordTokens;
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.mailer = mailer;
        this.events = events;
        this.clock = clock;
    }

    /**
     * "Esqueci a senha". Sempre termina sem erro, exista ou não o e-mail, para não revelar contas.
     * Usuário ainda não ativado recebe de novo o link de ativação.
     */
    @Transactional
    public void requestReset(String rawEmail, ClientInfo client) {
        Email email;
        try {
            email = new Email(rawEmail);
        } catch (DomainException e) {
            return;
        }
        users.findByEmail(email)
                .filter(u -> u.status() != UserStatus.DISABLED)
                .ifPresent(user -> {
                    TokenPurpose purpose = user.status() == UserStatus.PENDING_ACTIVATION
                            ? TokenPurpose.ACTIVATION : TokenPurpose.RESET;
                    sendLink(user, purpose, client.ip());
                });
    }

    /** Envia link de ativação a um usuário recém-criado (ou reenvia). */
    @Transactional
    public void sendActivation(User user, String requestedIp) {
        sendLink(user, TokenPurpose.ACTIVATION, requestedIp);
    }

    /** Define a senha a partir do link (ativação ou redefinição). Encerra todas as sessões abertas. */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        Instant now = clock.instant();
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException();
        }
        PasswordToken token = passwordTokens.findByHashForUpdate(SecureTokens.sha256(rawToken))
                .orElseThrow(InvalidTokenException::new);
        User user = users.findById(token.userId()).orElseThrow(InvalidTokenException::new);

        PasswordPolicy.validate(newPassword, user.email());
        token.consume(now);
        user.setPassword(hasher.hash(newPassword), now);

        passwordTokens.save(token);
        users.save(user);
        refreshTokens.revokeAllForUser(user.id(), now, RefreshToken.REASON_PASSWORD_CHANGED);
        events.passwordChanged(user, token.purpose() == TokenPurpose.ACTIVATION ? "ACTIVATION" : "RESET_LINK");
    }

    /** O usuário logado troca a própria senha informando a atual. */
    @Transactional
    public void change(UUID userId, String currentPassword, String newPassword) {
        Instant now = clock.instant();
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuário"));
        if (currentPassword == null || !hasher.matches(currentPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        if (currentPassword.equals(newPassword)) {
            throw new DomainException("same_password", "A nova senha precisa ser diferente da atual");
        }
        PasswordPolicy.validate(newPassword, user.email());
        user.setPassword(hasher.hash(newPassword), now);
        users.save(user);
        refreshTokens.revokeAllForUser(user.id(), now, RefreshToken.REASON_PASSWORD_CHANGED);
        events.passwordChanged(user, "SELF_SERVICE");
    }

    private void sendLink(User user, TokenPurpose purpose, String requestedIp) {
        Instant now = clock.instant();
        passwordTokens.invalidatePendingForUser(user.id(), now);
        String rawToken = SecureTokens.generate(LINK_TOKEN_BYTES);
        passwordTokens.save(PasswordToken.issue(user.id(), SecureTokens.sha256(rawToken), purpose, now, requestedIp));
        mailer.sendPasswordLink(user, purpose, rawToken);
        events.passwordLinkRequested(user, purpose.name(), requestedIp);
    }
}
