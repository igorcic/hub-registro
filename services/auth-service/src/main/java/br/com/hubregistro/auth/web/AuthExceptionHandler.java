package br.com.hubregistro.auth.web;

import br.com.hubregistro.auth.domain.AuthErrors.InvalidCredentialsException;
import br.com.hubregistro.auth.domain.AuthErrors.InvalidTokenException;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.security.web.HubExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Falhas de autenticação respondem 401 (o tratamento genérico de DomainException responderia 422). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {

    @ExceptionHandler({InvalidCredentialsException.class, InvalidTokenException.class})
    ProblemDetail unauthorized(DomainException e) {
        return HubExceptionHandler.problem(HttpStatus.UNAUTHORIZED, e.code(), e.getMessage());
    }
}
