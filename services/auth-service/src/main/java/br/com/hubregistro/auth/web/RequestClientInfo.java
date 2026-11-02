package br.com.hubregistro.auth.web;

import br.com.hubregistro.auth.application.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;

/**
 * IP e user agent da requisição. O serviço só é alcançado pelo gateway, que acrescenta X-Forwarded-For;
 * por isso o primeiro endereço desse cabeçalho é o do cliente.
 */
final class RequestClientInfo {

    private RequestClientInfo() {
    }

    static ClientInfo of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return new ClientInfo(ip, request.getHeader("User-Agent"));
    }
}
