package br.com.hubregistro.auth.application.port;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);

    /**
     * Gasta o mesmo tempo de uma verificação real. Usado quando o e-mail ou o cliente não existe,
     * para que o tempo de resposta não revele quais contas existem.
     */
    void simulateVerification(String rawPassword);
}
