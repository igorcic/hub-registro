package br.com.hubregistro.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RabbitMQ real com a mesma topologia do ambiente ({@code infra/rabbitmq/definitions.json}), acessado com o
 * usuário da aplicação ({@code hub_app}, sem permissão de criar filas), como no Compose.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestcontainersConfiguration {

    public static final DockerImageName RABBIT_IMAGE = DockerImageName.parse("rabbitmq:4.1-management-alpine")
            .asCompatibleSubstituteFor("rabbitmq");

    @Bean
    RabbitMQContainer rabbitContainer() throws Exception {
        RabbitMQContainer container = new RabbitMQContainer(RABBIT_IMAGE)
                .withCopyFileToContainer(MountableFile.forHostPath(definitionsFile()), "/tmp/definitions.json");
        container.start();
        Container.ExecResult result = container.execInContainer("rabbitmqctl", "import_definitions", "/tmp/definitions.json");
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Falha ao importar a topologia: " + result.getStderr());
        }
        return container;
    }

    @Bean
    DynamicPropertyRegistrar rabbitProperties(RabbitMQContainer container) {
        return registry -> {
            registry.add("spring.rabbitmq.host", container::getHost);
            registry.add("spring.rabbitmq.port", container::getAmqpPort);
            registry.add("spring.rabbitmq.username", () -> "hub_app");
            registry.add("spring.rabbitmq.password", () -> "hub_app_dev");
            registry.add("spring.rabbitmq.virtual-host", () -> "hub");
            registry.add("hub.messaging.relay.initial-delay", () -> "0s");
            registry.add("hub.messaging.relay.interval", () -> "200ms");
        };
    }

    /** Procura infra/rabbitmq/definitions.json subindo a partir do diretório do módulo em teste. */
    static Path definitionsFile() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("infra/rabbitmq/definitions.json");
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("infra/rabbitmq/definitions.json não encontrado");
    }
}
