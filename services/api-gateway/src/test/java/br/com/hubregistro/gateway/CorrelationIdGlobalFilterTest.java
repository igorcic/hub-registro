package br.com.hubregistro.gateway;

import br.com.hubregistro.observability.CorrelationIds;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

    @Test
    void forwardsValidIdToTheServiceAndReturnsItToTheCaller() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/contracts").header(CorrelationIds.HEADER, "bank-req-42"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, ex -> { forwarded.set(ex); return Mono.empty(); }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationIds.HEADER)).isEqualTo("bank-req-42");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER)).isEqualTo("bank-req-42");
    }

    @Test
    void generatesIdWhenAbsentAndUsesTheSameValueBothWays() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/banks"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, ex -> { forwarded.set(ex); return Mono.empty(); }).block();

        String sent = forwarded.get().getRequest().getHeaders().getFirst(CorrelationIds.HEADER);
        assertThat(CorrelationIds.isValid(sent)).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER)).isEqualTo(sent);
    }
}
