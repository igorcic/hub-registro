package br.com.hubregistro.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "hub.messaging")
public class MessagingProperties {

    private final Relay relay = new Relay();

    /**
     * Níveis de espera para retentativa, na ordem; cada um corresponde a uma fila contract.retry.&lt;nível&gt;
     * em definitions.json. Esgotados, a mensagem vai para a DLQ.
     */
    private List<String> retryLevels = List.of("10s", "1m", "5m", "30m");

    public static class Relay {
        private boolean enabled = true;
        private Duration interval = Duration.ofMillis(500);
        private int batchSize = 100;
        private Duration confirmTimeout = Duration.ofSeconds(5);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public Duration getConfirmTimeout() { return confirmTimeout; }
        public void setConfirmTimeout(Duration confirmTimeout) { this.confirmTimeout = confirmTimeout; }
    }

    public Relay getRelay() { return relay; }
    public List<String> getRetryLevels() { return retryLevels; }
    public void setRetryLevels(List<String> retryLevels) { this.retryLevels = retryLevels; }
}
