package br.com.hubregistro.auth.web.dto;

import br.com.hubregistro.auth.application.TokenPair;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 100) String refreshToken) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
                                long refreshExpiresIn) {
        public static TokenResponse of(TokenPair pair) {
            return new TokenResponse(pair.accessToken(), "Bearer", pair.accessTokenExpiresIn(), pair.refreshToken(),
                    pair.refreshTokenExpiresIn());
        }
    }

    public record PasswordResetRequest(@NotBlank @Size(max = 254) String email) {
    }

    public record PasswordResetConfirmation(@NotBlank @Size(max = 100) String token,
                                            @NotBlank @Size(max = 128) String newPassword) {
    }

    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                                        @NotBlank @Size(max = 128) String newPassword) {
    }

    public record MeResponse(String subject, boolean apiClient, String fullName, String email, InstitutionView institution,
                             List<String> roles, List<String> permissions) {
    }

    public record InstitutionView(UUID id, String type, String name) {
    }
}
