package br.com.hubregistro.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, JacksonAutoConfiguration.class, RabbitAutoConfiguration.class})
@ConditionalOnBean(JdbcTemplate.class)
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxWriter outboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper, Environment environment,
                              ObjectProvider<Clock> clock) {
        return new OutboxWriter(jdbc, objectMapper,
                environment.getProperty("spring.application.name", "unknown"),
                clock.getIfAvailable(Clock::systemUTC));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(RabbitTemplate.class)
    @EnableScheduling
    static class RabbitMessagingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OutboxRelay outboxRelay(JdbcTemplate jdbc, TransactionTemplate transaction, RabbitTemplate rabbit,
                                ObjectMapper objectMapper, MessagingProperties properties, ObjectProvider<Clock> clock) {
            return new OutboxRelay(jdbc, transaction, rabbit, objectMapper, properties, clock.getIfAvailable(Clock::systemUTC));
        }

        @Bean
        @ConditionalOnMissingBean
        ReliableMessageHandler reliableMessageHandler(JdbcTemplate jdbc, TransactionTemplate transaction,
                                                      RabbitTemplate rabbit, ObjectMapper objectMapper,
                                                      MessagingProperties properties) {
            return new ReliableMessageHandler(jdbc, transaction, rabbit, objectMapper, properties);
        }
    }
}
