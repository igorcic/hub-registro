package br.com.hubregistro.gateway;

import br.com.hubregistro.observability.CorrelationIds;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Ponto de origem do correlation id: aceita o enviado pelo sistema do banco (se seguro) ou gera um,
 * repassa ao serviço de destino e devolve ao chamador. Daí em diante ele viaja em cabeçalhos HTTP,
 * cabeçalhos de mensagem e logs até o callback final ao banco.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = CorrelationIds.resolve(exchange.getRequest().getHeaders().getFirst(CorrelationIds.HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CorrelationIds.HEADER, correlationId))
                .build();
        exchange.getResponse().getHeaders().set(CorrelationIds.HEADER, correlationId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
