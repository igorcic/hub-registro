package br.com.hubregistro.auth.web;

import br.com.hubregistro.auth.application.AuthenticationService;
import br.com.hubregistro.auth.application.port.AccessTokenIssuer.IssuedToken;
import br.com.hubregistro.auth.domain.AuthErrors.InvalidClientException;
import br.com.hubregistro.auth.infrastructure.security.SigningKeyStore;
import br.com.hubregistro.auth.web.dto.ApiClientDtos.OAuthError;
import br.com.hubregistro.auth.web.dto.ApiClientDtos.OAuthTokenResponse;
import br.com.hubregistro.commons.error.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Endpoints OAuth2: client credentials para os sistemas dos bancos e JWKS para os serviços validarem tokens. */
@RestController
@Tag(name = "OAuth2")
class OAuthController {

    private final AuthenticationService authentication;
    private final SigningKeyStore keys;

    OAuthController(AuthenticationService authentication, SigningKeyStore keys) {
        this.authentication = authentication;
        this.keys = keys;
    }

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "grant_type=client_credentials; credenciais por HTTP Basic ou no corpo")
    ResponseEntity<?> token(@RequestParam("grant_type") String grantType,
                            @RequestParam(value = "client_id", required = false) String clientId,
                            @RequestParam(value = "client_secret", required = false) String clientSecret,
                            @RequestParam(value = "scope", required = false) String scope,
                            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!"client_credentials".equals(grantType)) {
            return error(400, "unsupported_grant_type", "Somente client_credentials é suportado");
        }
        String[] basic = basicCredentials(authorization);
        if (basic != null) {
            clientId = basic[0];
            clientSecret = basic[1];
        }
        Set<String> scopes = scope == null || scope.isBlank()
                ? Set.of()
                : Arrays.stream(scope.trim().split("\\s+")).collect(Collectors.toSet());
        try {
            IssuedToken token = authentication.issueClientToken(clientId, clientSecret, scopes);
            String granted = scopes.isEmpty() ? null : String.join(" ", scopes);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(new OAuthTokenResponse(token.value(), "Bearer", token.expiresInSeconds(), granted));
        } catch (InvalidClientException e) {
            return error(401, "invalid_client", e.getMessage());
        } catch (DomainException e) {
            return error(400, "invalid_scope".equals(e.code()) ? "invalid_scope" : "invalid_request", e.getMessage());
        }
    }

    @GetMapping(value = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Chaves públicas para validar os access tokens")
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                .body(keys.publicKeys().toJSONObject());
    }

    private static String[] basicCredentials(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon < 0 ? null : new String[]{decoded.substring(0, colon), decoded.substring(colon + 1)};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ResponseEntity<OAuthError> error(int status, String code, String description) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if (status == 401) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"hub-registro\"");
        }
        return builder.body(new OAuthError(code, description));
    }
}
