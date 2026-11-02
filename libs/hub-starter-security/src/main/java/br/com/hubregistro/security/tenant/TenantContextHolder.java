package br.com.hubregistro.security.tenant;

import br.com.hubregistro.security.HubPrincipal;
import br.com.hubregistro.security.HubSecurity;

import java.util.function.Supplier;

/**
 * Resolve o tenant da thread atual: o definido explicitamente (consumidores de mensagens, jobs) tem
 * precedência; senão vem do usuário autenticado; sem nenhum dos dois, {@link TenantContext#NONE}
 * (o banco não devolve nenhuma linha protegida).
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> OVERRIDE = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static TenantContext current() {
        TenantContext override = OVERRIDE.get();
        if (override != null) {
            return override;
        }
        return HubSecurity.currentPrincipal().map(TenantContextHolder::fromPrincipal).orElse(TenantContext.NONE);
    }

    /** Executa como processo interno da plataforma (vê todos os tenants). Use só fora do fluxo de requisição. */
    public static <T> T runAsSystem(Supplier<T> action) {
        return runAs(TenantContext.SYSTEM, action);
    }

    public static void runAsSystem(Runnable action) {
        runAs(TenantContext.SYSTEM, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T runAs(TenantContext context, Supplier<T> action) {
        TenantContext previous = OVERRIDE.get();
        OVERRIDE.set(context);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                OVERRIDE.remove();
            } else {
                OVERRIDE.set(previous);
            }
        }
    }

    static TenantContext fromPrincipal(HubPrincipal principal) {
        return switch (principal.institutionType()) {
            case HUB -> new TenantContext("HUB", null);
            case BANK -> new TenantContext("BANK", principal.institutionId());
            case REGISTRY -> new TenantContext("REGISTRY", principal.institutionId());
        };
    }
}
