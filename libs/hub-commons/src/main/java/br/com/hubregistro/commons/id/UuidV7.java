package br.com.hubregistro.commons.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Gera UUID versão 7 (RFC 9562): 48 bits de timestamp em milissegundos seguidos de bits aleatórios.
 *
 * <p>Ids gerados na aplicação (e não pelo banco) permitem conhecer o id do agregado antes do INSERT,
 * usá-lo no evento do Outbox na mesma transação e mantê-los ordenáveis no tempo como o {@code uuidv7()}
 * do PostgreSQL.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        return generate(Clock.systemUTC());
    }

    public static UUID generate(Clock clock) {
        long millis = clock.millis();
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        long msb = (millis & 0xFFFF_FFFF_FFFFL) << 16;      // unix_ts_ms (48 bits)
        msb |= 0x7000L;                                     // versão 7
        msb |= ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL); // rand_a (12 bits)

        long lsb = 0;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (random[i] & 0xFFL);
        }
        lsb = (lsb & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L; // variante RFC (10xx)

        return new UUID(msb, lsb);
    }

    /** Milissegundos desde a época embutidos num UUID v7. */
    public static long timestampMillis(UUID uuid) {
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("Não é um UUID v7: " + uuid);
        }
        return uuid.getMostSignificantBits() >>> 16;
    }
}
