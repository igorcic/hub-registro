package br.com.hubregistro.gateway.ratelimit;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/** INCR + PEXPIRE atômicos (script Lua): uma chave nunca fica sem expiração, mesmo se o gateway cair no meio. */
@Component
class RedisRequestCounter implements RequestCounter {

    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count""", Long.class);

    private final ReactiveStringRedisTemplate redis;

    RedisRequestCounter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Long> increment(String key, Duration window) {
        return redis.execute(INCREMENT, List.of(key), List.of(String.valueOf(window.toMillis()))).next();
    }
}
