package br.com.hubregistro.commons.id;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    @Test
    void hasVersion7AndRfcVariant() {
        UUID id = UuidV7.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheClockTimestamp() {
        Instant now = Instant.parse("2026-11-02T14:00:00.123Z");

        UUID id = UuidV7.generate(Clock.fixed(now, ZoneOffset.UTC));

        assertThat(UuidV7.timestampMillis(id)).isEqualTo(now.toEpochMilli());
    }

    @Test
    void laterTimestampsSortAfterEarlierOnes() {
        UUID earlier = UuidV7.generate(Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC));
        UUID later = UuidV7.generate(Clock.fixed(Instant.parse("2026-11-02T10:00:01Z"), ZoneOffset.UTC));

        assertThat(earlier.toString()).isLessThan(later.toString());
    }

    @Test
    void doesNotCollide() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(UuidV7.generate());
        }
        assertThat(ids).hasSize(100_000);
    }
}
