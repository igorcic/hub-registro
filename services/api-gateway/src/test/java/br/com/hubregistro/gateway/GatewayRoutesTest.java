package br.com.hubregistro.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void everyServiceHasARoute() {
        List<String> routeIds = routeLocator.getRoutes().map(Route::getId).collectList().block();

        assertThat(routeIds).containsExactlyInAnyOrder(
                "auth-service", "bank-service", "registry-service", "contract-service", "document-service",
                "routing-service", "integration-service", "notification-service", "audit-service",
                "operations-service");
    }
}
