package br.com.hubregistro.auth.domain.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    boolean existsWithRole(String roleCode);

    UserPage search(UserSearch criteria, int page, int size);

    void save(User user);

    record UserSearch(UUID institutionId, UserStatus status, String text) {
    }

    record UserPage(List<User> items, long total, int page, int size) {
    }
}
