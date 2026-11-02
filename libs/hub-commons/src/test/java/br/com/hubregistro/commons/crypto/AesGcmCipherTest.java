package br.com.hubregistro.commons.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final AesGcmCipher cipher = AesGcmCipher.fromBase64Key(KEY);

    @Test
    void roundTrips() {
        byte[] sealed = cipher.encrypt(bytes("12345678909"), bytes("party-1"));

        assertThat(new String(cipher.decrypt(sealed, bytes("party-1")), StandardCharsets.UTF_8)).isEqualTo("12345678909");
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        assertThat(cipher.encrypt(bytes("x"), null)).isNotEqualTo(cipher.encrypt(bytes("x"), null));
    }

    @Test
    void valueMovedToAnotherContextDoesNotDecrypt() {
        byte[] sealed = cipher.encrypt(bytes("segredo"), bytes("registro-A"));

        assertThatThrownBy(() -> cipher.decrypt(sealed, bytes("registro-B"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperedCiphertextIsRejected() {
        byte[] sealed = cipher.encrypt(bytes("segredo"), null);
        sealed[sealed.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(sealed, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeysThatAreNot256Bits() {
        assertThatThrownBy(() -> new AesGcmCipher(new byte[16])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmCipher.fromBase64Key("")).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
