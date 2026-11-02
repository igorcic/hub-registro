package br.com.hubregistro.auth.web.dto;

import br.com.hubregistro.auth.domain.role.Role;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.auth.domain.user.UserRepository.UserPage;
import br.com.hubregistro.auth.domain.user.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(UUID id, UUID institutionId, String fullName, String email, UserStatus status,
                               boolean locked, List<String> roles, Instant lastLoginAt) {
        public static UserResponse of(User user, Clock clock) {
            return new UserResponse(user.id(), user.institutionId(), user.fullName(), user.email().value(), user.status(),
                    user.isLocked(clock.instant()), user.roleCodes().stream().sorted().toList(), user.lastLoginAt());
        }
    }

    public record PageResponse<T>(List<T> items, long total, int page, int size) {
        public static PageResponse<UserResponse> of(UserPage page, Clock clock) {
            return new PageResponse<>(page.items().stream().map(u -> UserResponse.of(u, clock)).toList(),
                    page.total(), page.page(), page.size());
        }
    }

    /** {@code institutionId} só é considerado para usuários do Hub; os demais criam sempre na própria instituição. */
    public record CreateUserRequest(UUID institutionId,
                                    @NotBlank @Size(max = 200) String fullName,
                                    @NotBlank @Size(max = 254) String email,
                                    @NotEmpty Set<@NotBlank String> roles) {
    }

    public record RenameUserRequest(@NotBlank @Size(max = 200) String fullName) {
    }

    public record ChangeStatusRequest(@NotNull UserStatus status, @NotBlank @Size(max = 500) String reason) {
    }

    public record ReplaceRolesRequest(@NotEmpty Set<@NotBlank String> roles) {
    }

    public record RoleResponse(String code, String name, String description, String institutionType,
                               List<String> permissions) {
        public static RoleResponse of(Role role) {
            return new RoleResponse(role.code(), role.name(), role.description(), role.institutionType().name(),
                    role.permissions().stream().sorted().toList());
        }
    }
}
