package br.com.hubregistro.auth.application;

import br.com.hubregistro.auth.application.port.AccessTokenIssuer;
import br.com.hubregistro.auth.application.port.AccessTokenIssuer.IssuedToken;
import br.com.hubregistro.auth.application.port.AuthEventPublisher;
import br.com.hubregistro.auth.application.port.PasswordHasher;
import br.com.hubregistro.auth.domain.AuthErrors.InvalidClientException;
import br.com.hubregistro.auth.domain.AuthErrors.InvalidCredentialsException;
import br.com.hubregistro.auth.domain.AuthErrors.InvalidTokenException;
import br.com.hubregistro.auth.domain.client.ApiClient;
import br.com.hubregistro.auth.domain.client.ApiClientRepository;
import br.com.hubregistro.auth.domain.institution.Institution;
import br.com.hubregistro.auth.domain.institution.InstitutionRepository;
import br.com.hubregistro.auth.domain.session.RefreshToken;
import br.com.hubregistro.auth.domain.session.RefreshTokenRepository;
import br.com.hubregistro.auth.domain.session.SessionPolicy;
import br.com.hubregistro.auth.domain.user.Email;
import br.com.hubregistro.auth.domain.user.LoginPolicy;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.auth.domain.user.UserRepository;
import br.com.hubregistro.commons.crypto.SecureTokens;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.tenant.InstitutionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Login, renovação e encerramento de sessão de usuários, e emissão de token para clientes de API.
 *
 * <p>Toda falha responde igual ({@link InvalidCredentialsException}): e-mail inexistente, senha errada, conta
 * bloqueada, desativada ou instituição inativa. O motivo real vai só para a auditoria.
 */
@Service
public class AuthenticationService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository users;
    private final InstitutionRepository institutions;
    private final RefreshTokenRepository refreshTokens;
    private final ApiClientRepository apiClients;
    private final PermissionResolver permissions;
    private final PasswordHasher hasher;
    private final AccessTokenIssuer tokenIssuer;
    private final AuthEventPublisher events;
    private final SessionPolicy sessionPolicy;
    private final LoginPolicy loginPolicy;
    private final Clock clock;

    public AuthenticationService(UserRepository users, InstitutionRepository institutions,
                                 RefreshTokenRepository refreshTokens, ApiClientRepository apiClients,
                                 PermissionResolver permissions, PasswordHasher hasher, AccessTokenIssuer tokenIssuer,
                                 AuthEventPublisher events, SessionPolicy sessionPolicy, LoginPolicy loginPolicy,
                                 Clock clock) {
        this.users = users;
        this.institutions = institutions;
        this.refreshTokens = refreshTokens;
        this.apiClients = apiClients;
        this.permissions = permissions;
        this.hasher = hasher;
        this.tokenIssuer = tokenIssuer;
        this.events = events;
        this.sessionPolicy = sessionPolicy;
        this.loginPolicy = loginPolicy;
        this.clock = clock;
    }

    /** Falhas não desfazem a transação: o contador de tentativas e o evento de auditoria precisam ficar gravados. */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public TokenPair login(String rawEmail, String password, ClientInfo client) {
        Instant now = clock.instant();
        Email email = parseEmail(rawEmail, password);

        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            hasher.simulateVerification(password);
            events.loginFailed(null, email, client.ip(), "UNKNOWN_EMAIL");
            throw new InvalidCredentialsException();
        }
        User user = found.get();

        if (user.isLocked(now)) {
            hasher.simulateVerification(password);
            events.loginFailed(user, email, client.ip(), "LOCKED");
            throw new InvalidCredentialsException();
        }
        if (!hasher.matches(password, user.passwordHash())) {
            boolean lockedNow = user.loginFailed(now, loginPolicy);
            users.save(user);
            events.loginFailed(user, email, client.ip(), "BAD_PASSWORD");
            if (lockedNow) {
                events.userLocked(user, client.ip());
            }
            throw new InvalidCredentialsException();
        }
        if (!user.canAuthenticate(now)) {
            events.loginFailed(user, email, client.ip(), "STATUS_" + user.status());
            throw new InvalidCredentialsException();
        }
        Institution institution = institutions.findById(user.institutionId()).orElse(null);
        if (institution == null || !institution.isActive()) {
            events.loginFailed(user, email, client.ip(), "INSTITUTION_INACTIVE");
            throw new InvalidCredentialsException();
        }

        user.loginSucceeded(now);
        users.save(user);
        events.loginSucceeded(user, client.ip());

        String rawRefresh = SecureTokens.generate(REFRESH_TOKEN_BYTES);
        RefreshToken refresh = RefreshToken.startSession(user.id(), SecureTokens.sha256(rawRefresh), now, sessionPolicy,
                client.ip(), client.userAgent());
        refreshTokens.save(refresh);
        return tokenPair(user, institution, rawRefresh, refresh, now);
    }

    /**
     * Troca o refresh token por um novo par. Reapresentar um token já trocado revoga a família inteira:
     * ou o usuário legítimo ou um atacante tem uma cópia antiga, e não dá para saber qual.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenPair refresh(String rawRefreshToken, ClientInfo client) {
        Instant now = clock.instant();
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidTokenException();
        }
        RefreshToken current = refreshTokens.findByHashForUpdate(SecureTokens.sha256(rawRefreshToken))
                .orElseThrow(InvalidTokenException::new);

        if (current.wasRotated()) {
            refreshTokens.revokeFamily(current.familyId(), now, RefreshToken.REASON_REUSE_DETECTED);
            events.refreshReuseDetected(current.userId(), current.familyId(), client.ip());
            throw new InvalidTokenException();
        }
        if (!current.isActive(now)) {
            throw new InvalidTokenException();
        }

        User user = users.findById(current.userId()).orElse(null);
        Institution institution = user == null ? null : institutions.findById(user.institutionId()).orElse(null);
        if (user == null || !user.canAuthenticate(now) || institution == null || !institution.isActive()) {
            refreshTokens.revokeFamily(current.familyId(), now, RefreshToken.REASON_ACCESS_REVOKED);
            throw new InvalidTokenException();
        }

        String rawNext = SecureTokens.generate(REFRESH_TOKEN_BYTES);
        RefreshToken next = current.rotate(SecureTokens.sha256(rawNext), now, sessionPolicy, client.ip(), client.userAgent());
        refreshTokens.save(current);
        refreshTokens.save(next);
        return tokenPair(user, institution, rawNext, next, now);
    }

    /** Encerra a sessão (a família do token). Idempotente: token desconhecido não é erro. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByHashForUpdate(SecureTokens.sha256(rawRefreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.familyId(), clock.instant(), RefreshToken.REASON_LOGOUT));
    }

    /** OAuth2 client credentials: o sistema do banco troca client_id + client_secret por um access token. */
    @Transactional
    public IssuedToken issueClientToken(String clientId, String clientSecret, Set<String> requestedScopes) {
        Instant now = clock.instant();
        if (clientId == null || clientSecret == null) {
            throw new InvalidClientException();
        }
        Optional<ApiClient> found = apiClients.findByClientId(clientId);
        if (found.isEmpty()) {
            hasher.simulateVerification(clientSecret);
            throw new InvalidClientException();
        }
        ApiClient client = found.get();
        if (!hasher.matches(clientSecret, client.secretHash()) || !client.isUsable(now)) {
            throw new InvalidClientException();
        }
        Institution institution = institutions.findById(client.institutionId())
                .filter(Institution::isActive)
                .filter(i -> i.type() == InstitutionType.BANK)
                .orElseThrow(InvalidClientException::new);

        Set<String> scopes = client.grantScopes(requestedScopes);
        client.recordUse(now);
        apiClients.save(client);
        return tokenIssuer.issueForClient(client, institution, scopes, sessionPolicy.accessTokenTtl());
    }

    private TokenPair tokenPair(User user, Institution institution, String rawRefresh, RefreshToken refresh, Instant now) {
        IssuedToken access = tokenIssuer.issueForUser(user, institution, permissions.permissionsOf(user),
                sessionPolicy.accessTokenTtl());
        long refreshExpiresIn = Duration.between(now, refresh.expiresAt()).toSeconds();
        return new TokenPair(access.value(), access.expiresInSeconds(), rawRefresh, refreshExpiresIn);
    }

    private Email parseEmail(String rawEmail, String password) {
        try {
            return new Email(rawEmail);
        } catch (DomainException e) {
            hasher.simulateVerification(password == null ? "" : password);
            throw new InvalidCredentialsException();
        }
    }
}
