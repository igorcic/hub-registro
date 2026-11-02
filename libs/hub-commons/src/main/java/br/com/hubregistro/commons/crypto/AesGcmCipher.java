package br.com.hubregistro.commons.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifra autenticada AES-256-GCM para dados sensíveis em repouso (credenciais, chaves privadas, CPF/CNPJ).
 *
 * <p>Formato do texto cifrado: {@code versão(1) | nonce(12) | ciphertext+tag(16)}. O byte de versão permite
 * trocar o algoritmo ou a chave no futuro sem ambiguidade. O {@code associatedData} amarra o valor ao seu
 * contexto (ex.: o id do registro): um valor copiado para outra linha não decifra.
 */
public final class AesGcmCipher {

    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public AesGcmCipher(byte[] key) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("A chave AES-256 precisa ter exatamente 32 bytes");
        }
        this.key = new SecretKeySpec(Arrays.copyOf(key, key.length), "AES");
    }

    /** Cria a partir de uma chave de 32 bytes codificada em Base64 (formato das variáveis de ambiente). */
    public static AesGcmCipher fromBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("Chave de criptografia não configurada");
        }
        return new AesGcmCipher(Base64.getDecoder().decode(base64Key.trim()));
    }

    public byte[] encrypt(byte[] plaintext, byte[] associatedData) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            if (associatedData != null) {
                cipher.updateAAD(associatedData);
            }
            byte[] sealed = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(1 + NONCE_BYTES + sealed.length)
                    .put(FORMAT_VERSION).put(nonce).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar", e);
        }
    }

    public byte[] decrypt(byte[] ciphertext, byte[] associatedData) {
        if (ciphertext == null || ciphertext.length < 1 + NONCE_BYTES + TAG_BITS / 8) {
            throw new IllegalArgumentException("Texto cifrado inválido");
        }
        if (ciphertext[0] != FORMAT_VERSION) {
            throw new IllegalArgumentException("Versão de formato desconhecida: " + ciphertext[0]);
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, ciphertext, 1, NONCE_BYTES));
            if (associatedData != null) {
                cipher.updateAAD(associatedData);
            }
            return cipher.doFinal(ciphertext, 1 + NONCE_BYTES, ciphertext.length - 1 - NONCE_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar: chave, contexto ou conteúdo não conferem", e);
        }
    }
}
