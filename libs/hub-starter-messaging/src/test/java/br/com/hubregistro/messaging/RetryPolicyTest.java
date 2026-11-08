package br.com.hubregistro.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    @Test
    void relayBackoffGrowsExponentiallyUpToFiveMinutes() {
        assertThat(OutboxRelay.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxRelay.backoff(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(OutboxRelay.backoff(9)).isEqualTo(Duration.ofMinutes(5));
        assertThat(OutboxRelay.backoff(1_000)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void retryCountReadsTheHeaderWhateverItsType() {
        MessageProperties none = new MessageProperties();
        MessageProperties number = new MessageProperties();
        number.setHeader(ReliableMessageHandler.RETRY_COUNT_HEADER, 2);
        MessageProperties text = new MessageProperties();
        text.setHeader(ReliableMessageHandler.RETRY_COUNT_HEADER, "3");

        assertThat(ReliableMessageHandler.retryCount(none)).isZero();
        assertThat(ReliableMessageHandler.retryCount(number)).isEqualTo(2);
        assertThat(ReliableMessageHandler.retryCount(text)).isEqualTo(3);
    }
}
