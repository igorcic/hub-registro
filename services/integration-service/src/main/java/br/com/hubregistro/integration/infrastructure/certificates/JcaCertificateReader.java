package br.com.hubregistro.integration.infrastructure.certificates;

import br.com.hubregistro.commons.crypto.SecureTokens;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.id.UuidV7;
import br.com.hubregistro.integration.application.CertificateReader;
import br.com.hubregistro.integration.domain.Certificate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aceita PKCS#12 (.p12/.pfx, com senha) ou PEM (certificado e, opcionalmente, chave privada PKCS#8).
 * Guarda tudo como PKCS#12: para PEM gera uma senha aleatória, guardada cifrada junto do conteúdo.
 */
@Component
class JcaCertificateReader implements CertificateReader {

    private static final int MAX_SIZE = 64 * 1024;
    private static final Pattern PEM_KEY = Pattern.compile(
            "-----BEGIN PRIVATE KEY-----([A-Za-z0-9+/=\\s]+)-----END PRIVATE KEY-----");

    @Override
    public ParsedCertificate read(UUID integrationId, String alias, Certificate.Purpose purpose, byte[] content, String password) {
        if (content == null || content.length == 0 || content.length > MAX_SIZE) {
            throw new DomainException("invalid_certificate", "Arquivo vazio ou maior que 64 KB");
        }
        try {
            String text = new String(content, StandardCharsets.US_ASCII);
            return text.contains("-----BEGIN ")
                    ? fromPem(integrationId, alias, purpose, text)
                    : fromPkcs12(integrationId, alias, purpose, content, password);
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("invalid_certificate", "Não foi possível ler o certificado: " + e.getMessage());
        }
    }

    private ParsedCertificate fromPkcs12(UUID integrationId, String alias, Certificate.Purpose purpose, byte[] content,
                                         String password) throws Exception {
        if (password == null) {
            throw new DomainException("password_required", "Informe a senha do arquivo PKCS#12");
        }
        KeyStore source = KeyStore.getInstance("PKCS12");
        try {
            source.load(new ByteArrayInputStream(content), password.toCharArray());
        } catch (Exception e) {
            throw new DomainException("invalid_certificate", "Senha incorreta ou arquivo PKCS#12 inválido");
        }
        String keyAlias = null;
        String certAlias = null;
        for (String a : Collections.list(source.aliases())) {
            if (keyAlias == null && source.isKeyEntry(a)) {
                keyAlias = a;
            }
            if (certAlias == null && source.getCertificate(a) != null) {
                certAlias = a;
            }
        }
        String leafAlias = keyAlias != null ? keyAlias : certAlias;
        if (leafAlias == null) {
            throw new DomainException("invalid_certificate", "Arquivo sem certificado");
        }
        X509Certificate leaf = (X509Certificate) source.getCertificate(leafAlias);
        requireKeyIfClientAuth(purpose, keyAlias != null);

        KeyStore target = KeyStore.getInstance("PKCS12");
        target.load(null, null);
        String storePassword = SecureTokens.generate(24);
        if (keyAlias != null) {
            Key key = source.getKey(keyAlias, password.toCharArray());
            target.setKeyEntry(alias, key, storePassword.toCharArray(), source.getCertificateChain(keyAlias));
        } else {
            target.setCertificateEntry(alias, leaf);
        }
        return new ParsedCertificate(metadata(integrationId, alias, purpose, "PKCS12", leaf), store(target, storePassword), storePassword);
    }

    private ParsedCertificate fromPem(UUID integrationId, String alias, Certificate.Purpose purpose, String text) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> chain = new ArrayList<>();
        for (java.security.cert.Certificate c : factory.generateCertificates(
                new ByteArrayInputStream(stripKeys(text).getBytes(StandardCharsets.US_ASCII)))) {
            chain.add((X509Certificate) c);
        }
        if (chain.isEmpty()) {
            throw new DomainException("invalid_certificate", "Nenhum certificado encontrado no PEM");
        }
        if (text.contains("BEGIN RSA PRIVATE KEY") || text.contains("BEGIN EC PRIVATE KEY")) {
            throw new DomainException("invalid_certificate",
                    "Chave em formato PKCS#1/SEC1: converta para PKCS#8 (openssl pkcs8 -topk8 -nocrypt) ou envie um PKCS#12");
        }
        PrivateKey key = null;
        Matcher matcher = PEM_KEY.matcher(text);
        if (matcher.find()) {
            byte[] der = Base64.getMimeDecoder().decode(matcher.group(1));
            key = parseKey(der);
        }
        requireKeyIfClientAuth(purpose, key != null);

        KeyStore target = KeyStore.getInstance("PKCS12");
        target.load(null, null);
        String storePassword = SecureTokens.generate(24);
        if (key != null) {
            target.setKeyEntry(alias, key, storePassword.toCharArray(), chain.toArray(new X509Certificate[0]));
        } else {
            for (int i = 0; i < chain.size(); i++) {
                target.setCertificateEntry(i == 0 ? alias : alias + "-" + i, chain.get(i));
            }
        }
        return new ParsedCertificate(metadata(integrationId, alias, purpose, "PEM", chain.getFirst()), store(target, storePassword), storePassword);
    }

    private static PrivateKey parseKey(byte[] der) {
        for (String algorithm : List.of("RSA", "EC", "Ed25519")) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(der));
            } catch (Exception ignored) {
                // tenta o próximo algoritmo
            }
        }
        throw new DomainException("invalid_certificate", "Chave privada não reconhecida (RSA, EC ou Ed25519 em PKCS#8)");
    }

    private static String stripKeys(String text) {
        return text.replaceAll("(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", "");
    }

    private static void requireKeyIfClientAuth(Certificate.Purpose purpose, boolean hasKey) {
        if (purpose == Certificate.Purpose.CLIENT_AUTH && !hasKey) {
            throw new DomainException("private_key_required", "Certificado de cliente (mTLS) precisa da chave privada");
        }
    }

    private static Certificate metadata(UUID integrationId, String alias, Certificate.Purpose purpose, String type,
                                        X509Certificate cert) throws Exception {
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        return new Certificate(UuidV7.generate(), integrationId, alias, purpose, type,
                cert.getSubjectX500Principal().getName(), cert.getIssuerX500Principal().getName(),
                cert.getSerialNumber().toString(16), cert.getNotBefore().toInstant(), cert.getNotAfter().toInstant(),
                fingerprint, true, Instant.now());
    }

    private static byte[] store(KeyStore keyStore, String password) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keyStore.store(out, password.toCharArray());
        return out.toByteArray();
    }
}
