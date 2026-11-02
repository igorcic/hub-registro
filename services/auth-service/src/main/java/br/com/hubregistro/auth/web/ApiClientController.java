package br.com.hubregistro.auth.web;

import br.com.hubregistro.auth.application.ApiClientService;
import br.com.hubregistro.auth.application.ApiClientService.CreateApiClient;
import br.com.hubregistro.auth.application.ApiClientService.CreatedApiClient;
import br.com.hubregistro.auth.web.dto.ApiClientDtos.ApiClientResponse;
import br.com.hubregistro.auth.web.dto.ApiClientDtos.CreateApiClientRequest;
import br.com.hubregistro.auth.web.dto.ApiClientDtos.CreatedApiClientResponse;
import br.com.hubregistro.security.HubSecurity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-clients")
@PreAuthorize("hasAuthority('api-client:manage')")
@Tag(name = "Credenciais de API dos bancos")
class ApiClientController {

    private final ApiClientService service;

    ApiClientController(ApiClientService service) {
        this.service = service;
    }

    @GetMapping
    List<ApiClientResponse> list(@RequestParam(required = false) UUID institutionId) {
        return service.list(HubSecurity.requirePrincipal(), institutionId).stream().map(ApiClientResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria a credencial; o clientSecret aparece só nesta resposta")
    CreatedApiClientResponse create(@Valid @RequestBody CreateApiClientRequest body) {
        CreatedApiClient created = service.create(HubSecurity.requirePrincipal(), new CreateApiClient(
                body.institutionId(), body.name(), body.scopes(), body.rateLimitPerMinute(), body.expiresAt()));
        return new CreatedApiClientResponse(ApiClientResponse.of(created.client()), created.clientSecret());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoga a credencial; tokens já emitidos expiram em até 15 minutos")
    void revoke(@PathVariable UUID id) {
        service.revoke(HubSecurity.requirePrincipal(), id);
    }
}
