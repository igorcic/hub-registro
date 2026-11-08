package br.com.hubregistro.test;

import java.time.Duration;
import java.util.function.Supplier;

/** Espera assíncrona simples para testes de mensageria (sem dependência extra). */
public final class Eventually {

    private Eventually() {
    }

    public static <T> T await(Duration timeout, Supplier<T> probe, java.util.function.Predicate<T> done) {
        long deadline = System.nanoTime() + timeout.toNanos();
        T value = probe.get();
        while (!done.test(value)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condição não atendida em " + timeout + "; último valor: " + value);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrompido", e);
            }
            value = probe.get();
        }
        return value;
    }

    public static void await(Duration timeout, Supplier<Boolean> condition) {
        await(timeout, condition, Boolean.TRUE::equals);
    }
}
