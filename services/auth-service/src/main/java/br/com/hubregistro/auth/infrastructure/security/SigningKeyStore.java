package br.com.hubregistro.auth.infrastructure.security;

import br.com.hubregistro.commons.crypto.AesGcmCipher;
import br.com.hubregistro.commons.crypto.SecureTokens;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Chaves RSA de assinatura dos tokens, guardadas em {@code signing_keys} com a chave privada cifrada (AES-GCM).
 *
 * <p>Na primeira inicialização gera a chave ativa (com advisory lock, para várias instâncias não gerarem duas).
 * O JWKS publica a chave ativa e as aposentadas nas últimas 24 h, para que tokens emitidos antes de uma rotação
 * continuem válidos até expirarem.
 */
@Component
public class SigningKeyStore {

    private static final Duration RETIRED_KEY_GRACE = Duration.ofHours(24);
    private static final DateTimeFormatter KID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final AesGcmCipher cipher;
    private final Clock clock;

    private volatile Keys keys;

    SigningKeyStore(JdbcTemplate jdbc, TransactionTemplate transaction, AesGcmCipher authCipher, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.cipher = authCipher;
        this.clock = clock;
    }

    private record Keys(RSAKey active, JWKSet publicSet) {
    }

    /** Chave privada ativa, para assinar. */
    public RSAKey activeSigningKey() {
        return keys().active();
    }

    /** Chaves públicas válidas, para o endpoint JWKS e para validar tokens localmente. */
    public JWKSet publicKeys() {
        return keys().publicSet();
    }

    @EventListener(ApplicationReadyEvent.class)
    void warmUp() {
        keys();
    }

    private Keys keys() {
        Keys current = keys;
        if (current == null) {
            synchronized (this) {
                current = keys;
                if (current == null) {
                    current = transaction.execute(status -> {
                        ensureActiveKey();
                        return load();
                    });
                    keys = current;
                }
            }
        }
        return current;
    }

    private void ensureActiveKey() {
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtext('auth.signing-key'))");
        Integer active = jdbc.queryForObject("SELECT count(*) FROM signing_keys WHERE retired_at IS NULL", Integer.class);
        if (active != null && active > 0) {
            return;
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Instant now = clock.instant();
            String kid = "k" + KID_DATE.format(now) + "-" + SecureTokens.generateHex(4);
            jdbc.update("""
                    INSERT INTO signing_keys (kid, algorithm, public_key_der, private_key_enc, created_at)
                    VALUES (?, 'RS256', ?, ?, ?)""",
                    kid, pair.getPublic().getEncoded(),
                    cipher.encrypt(pair.getPrivate().getEncoded(), kid.getBytes(StandardCharsets.UTF_8)),
                    Timestamp.from(now));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar a chave de assinatura", e);
        }
    }

    private Keys load() {
        Timestamp graceLimit = Timestamp.from(clock.instant().minus(RETIRED_KEY_GRACE));
        List<RSAKey> rows = jdbc.query("""
                SELECT kid, public_key_der, private_key_enc, retired_at FROM signing_keys
                WHERE retired_at IS NULL OR retired_at > ?
                ORDER BY retired_at NULLS FIRST, created_at DESC""",
                (rs, i) -> toKey(rs.getString("kid"), rs.getBytes("public_key_der"),
                        rs.getTimestamp("retired_at") == null ? rs.getBytes("private_key_enc") : null),
                graceLimit);
        if (rows.isEmpty() || !rows.getFirst().isPrivate()) {
            throw new IllegalStateException("Nenhuma chave de assinatura ativa");
        }
        return new Keys(rows.getFirst(), new JWKSet(rows.stream().map(k -> (com.nimbusds.jose.jwk.JWK) k.toPublicJWK()).toList()));
    }

    private RSAKey toKey(String kid, byte[] publicDer, byte[] privateEnc) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(publicDer));
            RSAKey.Builder builder = new RSAKey.Builder(publicKey)
                    .keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256);
            if (privateEnc != null) {
                byte[] pkcs8 = cipher.decrypt(privateEnc, kid.getBytes(StandardCharsets.UTF_8));
                builder.privateKey((RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8)));
            }
            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Chave de assinatura ilegível: " + kid, e);
        }
    }
}
