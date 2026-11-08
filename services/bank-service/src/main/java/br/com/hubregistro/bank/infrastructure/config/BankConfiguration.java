package br.com.hubregistro.bank.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class BankConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
