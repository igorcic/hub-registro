package br.com.hubregistro.gateway.ratelimit;

import reactor.core.publisher.Mono;

import java.time.Duration;

/** Contador compartilhado entre as instâncias do gateway. */
public interface RequestCounter {

    /** Incrementa e devolve o total da chave; a chave expira sozinha após {@code window}. */
    Mono<Long> increment(String key, Duration window);
}
