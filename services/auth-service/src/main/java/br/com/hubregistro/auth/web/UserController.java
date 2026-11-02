package br.com.hubregistro.auth.web;

import br.com.hubregistro.auth.application.UserManagementService;
import br.com.hubregistro.auth.application.UserManagementService.CreateUser;
import br.com.hubregistro.auth.domain.user.UserStatus;
import br.com.hubregistro.auth.web.dto.UserDtos.ChangeStatusRequest;
import br.com.hubregistro.auth.web.dto.UserDtos.CreateUserRequest;
import br.com.hubregistro.auth.web.dto.UserDtos.PageResponse;
import br.com.hubregistro.auth.web.dto.UserDtos.RenameUserRequest;
import br.com.hubregistro.auth.web.dto.UserDtos.ReplaceRolesRequest;
import br.com.hubregistro.auth.web.dto.UserDtos.RoleResponse;
import br.com.hubregistro.auth.web.dto.UserDtos.UserResponse;
import br.com.hubregistro.security.HubSecurity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Usuários e papéis")
class UserController {

    private final UserManagementService service;
    private final Clock clock;

    UserController(UserManagementService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:read')")
    @Operation(summary = "Lista usuários; fora do Hub, sempre só os da própria instituição")
    PageResponse<UserResponse> list(@RequestParam(required = false) UUID institutionId,
                                    @RequestParam(required = false) UserStatus status,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.search(HubSecurity.requirePrincipal(), institutionId, status, search, page, size), clock);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    UserResponse get(@PathVariable UUID id) {
        return UserResponse.of(service.get(HubSecurity.requirePrincipal(), id), clock);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('user:manage', 'user:manage-any')")
    @Operation(summary = "Cria usuário pendente de ativação e envia o link por e-mail")
    UserResponse create(@Valid @RequestBody CreateUserRequest body, HttpServletRequest request) {
        CreateUser command = new CreateUser(body.institutionId(), body.fullName(), body.email(), body.roles());
        return UserResponse.of(service.create(HubSecurity.requirePrincipal(), command, RequestClientInfo.of(request)), clock);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAnyAuthority('user:manage', 'user:manage-any')")
    UserResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameUserRequest body) {
        return UserResponse.of(service.rename(HubSecurity.requirePrincipal(), id, body.fullName()), clock);
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasAnyAuthority('user:manage', 'user:manage-any')")
    @Operation(summary = "Ativa ou desativa; desativar encerra as sessões do usuário")
    UserResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest body) {
        return UserResponse.of(service.changeStatus(HubSecurity.requirePrincipal(), id, body.status(), body.reason()), clock);
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAnyAuthority('user:manage', 'user:manage-any')")
    @Operation(summary = "Substitui os papéis; encerra as sessões para que os novos papéis valham no próximo login")
    UserResponse replaceRoles(@PathVariable UUID id, @Valid @RequestBody ReplaceRolesRequest body) {
        return UserResponse.of(service.replaceRoles(HubSecurity.requirePrincipal(), id, body.roles()), clock);
    }

    @PostMapping("/users/{id}/activation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyAuthority('user:manage', 'user:manage-any')")
    @Operation(summary = "Reenvia o link de ativação")
    void resendActivation(@PathVariable UUID id, HttpServletRequest request) {
        service.resendActivation(HubSecurity.requirePrincipal(), id, RequestClientInfo.of(request));
    }

    @GetMapping("/roles")
    @Operation(summary = "Papéis disponíveis para a instituição do chamador, com suas permissões")
    List<RoleResponse> roles() {
        return service.visibleRoles(HubSecurity.requirePrincipal()).stream().map(RoleResponse::of).toList();
    }
}
