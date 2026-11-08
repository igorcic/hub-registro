package br.com.hubregistro.integration.infrastructure.http;

import br.com.hubregistro.integration.application.HealthProbe;
import br.com.hubregistro.integration.domain.AuthType;
import br.com.hubregistro.integration.domain.Certificate;
import br.com.hubregistro.integration.domain.Endpoint;
import br.com.hubregistro.integration.domain.HealthCheckResult;
import br.com.hubregistro.integration.domain.IntegrationRepository;
import br.com.hubregistro.integration.domain.IntegrationRepository.StoredKeystore;
import br.com.hubregistro.integration.domain.Operation;
import br.com.hubregistro.integration.domain.RegistryIntegration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Verificação real por HTTP com os timeouts, a autenticação e (em mTLS) o certificado da integração.
 * Não segue redirecionamentos: um 3xx num endpoint de saúde indica configuração errada.
 */
@Component
class HttpHealthProbe implements HealthProbe {

    private final IntegrationRepository integrations;
    private final ObjectMapper json;
    private final Clock clock = Clock.systemUTC();

    HttpHealthProbe(IntegrationRepository integrations, ObjectMapper json) {
        this.integrations = integrations;
        this.json = json;
    }

    @Override
    public HealthCheckResult check(RegistryIntegration integration) {
        Instant start = clock.instant();
        long startNanos = System.nanoTime();
        try {
            HttpClient client = client(integration);
            Endpoint endpoint = integration.endpoints().getOrDefault(Operation.HEALTH_CHECK, new Endpoint("GET", "/"));
            HttpRequest.Builder request = HttpRequest.newBuilder(integration.resolve(endpoint))
                    .timeout(Duration.ofMillis(integration.readTimeoutMs()))
                    .header("User-Agent", "hub-registro-health-check/1.0")
                    .method(endpoint.httpMethod(), HttpRequest.BodyPublishers.noBody());
            authenticate(client, integration, request);
            HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            return HealthCheckResult.fromResponse(response.statusCode(), elapsed(startNanos), start);
        } catch (HttpTimeoutException e) {
            return HealthCheckResult.failure("Sem resposta dentro do timeout", elapsed(startNanos), start);
        } catch (ConnectException e) {
            return HealthCheckResult.failure("Conexão recusada ou host inalcançável", elapsed(startNanos), start);
        } catch (SSLException e) {
            return HealthCheckResult.failure("Falha de TLS: " + e.getMessage(), elapsed(startNanos), start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HealthCheckResult.failure("Verificação interrompida", elapsed(startNanos), start);
        } catch (Exception e) {
            return HealthCheckResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsed(startNanos), start);
        }
    }

    private HttpClient client(RegistryIntegration integration) throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(integration.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER);
        SSLContext ssl = sslContext(integration);
        if (ssl != null) {
            builder.sslContext(ssl);
        }
        return builder.build();
    }

    private void authenticate(HttpClient client, RegistryIntegration integration, HttpRequest.Builder request) throws Exception {
        Map<String, String> c = integration.credentials();
        switch (integration.authType()) {
            case API_KEY -> request.header(c.get("headerName"), c.get("apiKey"));
            case BASIC -> request.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString((c.get("username") + ":" + c.get("password")).getBytes(StandardCharsets.UTF_8)));
            case OAUTH2_CLIENT_CREDENTIALS -> request.header("Authorization", "Bearer " + fetchToken(client, integration, c));
            case NONE, MTLS -> { }
        }
    }

    private String fetchToken(HttpClient client, RegistryIntegration integration, Map<String, String> c) throws Exception {
        String form = "grant_type=client_credentials"
                + (c.containsKey("scope") ? "&scope=" + URLEncoder.encode(c.get("scope"), StandardCharsets.UTF_8) : "");
        String basic = Base64.getEncoder().encodeToString((c.get("clientId") + ":" + c.get("clientSecret")).getBytes(StandardCharsets.UTF_8));
        HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(c.get("tokenUrl")))
                .timeout(Duration.ofMillis(integration.readTimeoutMs()))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Token OAuth2 recusado (HTTP " + response.statusCode() + ")");
        }
        JsonNode token = json.readTree(response.body()).get("access_token");
        if (token == null || token.isNull()) {
            throw new IllegalStateException("Resposta OAuth2 sem access_token");
        }
        return token.asText();
    }

    /** mTLS: certificado de cliente da integração; confiança: certificado SERVER_TRUST se houver, senão a padrão. */
    private SSLContext sslContext(RegistryIntegration integration) throws Exception {
        StoredKeystore trust = integrations.activeCertificate(integration.id(), null, Certificate.Purpose.SERVER_TRUST).orElse(null);
        StoredKeystore client = integration.authType() == AuthType.MTLS
                ? integrations.activeCertificate(integration.id(), integration.credentials().get("certificateAlias"),
                        Certificate.Purpose.CLIENT_AUTH)
                .orElseThrow(() -> new IllegalStateException("Certificado de cliente não cadastrado ou vencido"))
                : null;
        if (trust == null && client == null) {
            return null;
        }
        KeyManagerFactory kmf = null;
        if (client != null) {
            KeyStore ks = load(client);
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, client.password().toCharArray());
        }
        TrustManagerFactory tmf = null;
        if (trust != null) {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(load(trust));
        }
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf == null ? null : kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), null);
        return context;
    }

    private static KeyStore load(StoredKeystore stored) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(stored.keystoreBytes()), stored.password().toCharArray());
        return ks;
    }

    private static int elapsed(long startNanos) {
        return (int) Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
