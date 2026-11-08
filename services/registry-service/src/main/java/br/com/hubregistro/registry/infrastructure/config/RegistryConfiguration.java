package br.com.hubregistro.registry.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class RegistryConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
