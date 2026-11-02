package br.com.hubregistro.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnBean(JdbcTemplate.class)
public class MessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxWriter outboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper, Environment environment,
                              ObjectProvider<Clock> clock) {
        return new OutboxWriter(jdbc, objectMapper,
                environment.getProperty("spring.application.name", "unknown"),
                clock.getIfAvailable(Clock::systemUTC));
    }
}
