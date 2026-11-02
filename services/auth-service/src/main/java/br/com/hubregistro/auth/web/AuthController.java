package br.com.hubregistro.auth.web;

import br.com.hubregistro.auth.application.AuthenticationService;
import br.com.hubregistro.auth.application.PasswordService;
import br.com.hubregistro.auth.application.ProfileService;
import br.com.hubregistro.auth.application.ProfileService.Profile;
import br.com.hubregistro.auth.web.dto.AuthDtos.ChangePasswordRequest;
import br.com.hubregistro.auth.web.dto.AuthDtos.InstitutionView;
import br.com.hubregistro.auth.web.dto.AuthDtos.LoginRequest;
import br.com.hubregistro.auth.web.dto.AuthDtos.MeResponse;
import br.com.hubregistro.auth.web.dto.AuthDtos.PasswordResetConfirmation;
import br.com.hubregistro.auth.web.dto.AuthDtos.PasswordResetRequest;
import br.com.hubregistro.auth.web.dto.AuthDtos.RefreshRequest;
import br.com.hubregistro.auth.web.dto.AuthDtos.TokenResponse;
import br.com.hubregistro.commons.error.ForbiddenOperationException;
import br.com.hubregistro.security.HubPrincipal;
import br.com.hubregistro.security.HubSecurity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação")
class AuthController {

    private final AuthenticationService authentication;
    private final PasswordService passwords;
    private final ProfileService profiles;

    AuthController(AuthenticationService authentication, PasswordService passwords, ProfileService profiles) {
        this.authentication = authentication;
        this.passwords = passwords;
        this.profiles = profiles;
    }

    @PostMapping("/login")
    @Operation(summary = "Login com e-mail e senha; devolve access token (15 min) e refresh token")
    TokenResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return TokenResponse.of(authentication.login(body.email(), body.password(), RequestClientInfo.of(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Troca o refresh token por um novo par; o token usado deixa de valer")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
        return TokenResponse.of(authentication.refresh(body.refreshToken(), RequestClientInfo.of(request)));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerra a sessão do refresh token informado")
    void logout(@Valid @RequestBody RefreshRequest body) {
        authentication.logout(body.refreshToken());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Envia link de redefinição; responde 202 exista ou não o e-mail")
    void requestPasswordReset(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        passwords.requestReset(body.email(), RequestClientInfo.of(request));
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Define a senha com o token do link (ativação ou redefinição)")
    void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmation body) {
        passwords.confirm(body.token(), body.newPassword());
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "O usuário logado troca a própria senha; encerra as demais sessões")
    void changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        HubPrincipal principal = HubSecurity.requirePrincipal();
        if (principal.apiClient()) {
            throw new ForbiddenOperationException("Clientes de API não têm senha");
        }
        passwords.change(UUID.fromString(principal.subject()), body.currentPassword(), body.newPassword());
    }

    @GetMapping("/me")
    @Operation(summary = "Quem sou eu: instituição, papéis e permissões (monta o menu do portal)")
    MeResponse me() {
        HubPrincipal p = HubSecurity.requirePrincipal();
        Profile profile = profiles.describe(p);
        InstitutionView institution = new InstitutionView(profile.institution().id(),
                profile.institution().type().name(), profile.institution().name());
        return new MeResponse(p.subject(), p.apiClient(), profile.fullName(), p.email(), institution,
                p.roles().stream().sorted().toList(), p.permissions().stream().sorted().toList());
    }
}
