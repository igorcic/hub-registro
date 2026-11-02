package br.com.hubregistro.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** 401 e 403 gerados pelo Spring Security (antes de chegar ao controller), no mesmo formato Problem Details. */
public final class ProblemDetailsSecurityHandlers {

    private final ObjectMapper objectMapper;

    public ProblemDetailsSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, e) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            write(response, HttpStatus.UNAUTHORIZED, "unauthorized", "Token ausente, inválido ou expirado");
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, e) -> write(response, HttpStatus.FORBIDDEN, "forbidden", "Sem permissão para esta operação");
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        ProblemDetail problem = HubExceptionHandler.problem(status, code, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
