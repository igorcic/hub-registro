package br.com.hubregistro.bank.domain;

import br.com.hubregistro.commons.document.Cnpj;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.id.UuidV7;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Instituição financeira cliente do Hub. CNPJ e código identificador não mudam depois do cadastro
 * (são referenciados por contratos e integrações).
 */
public class Bank {

    public static final int DEFAULT_REQUEST_LIMIT = 600;
    public static final int MAX_CONTACTS = 20;
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{2,30}$");

    private final UUID id;
    private final Cnpj cnpj;
    private final String code;
    private String legalName;
    private String tradeName;
    private BankStatus status;
    private String statusReason;
    private int requestLimitPerMinute;
    private Integer dailyContractLimit;
    private Map<String, Object> processingSettings;
    private List<BankContact> contacts;
    private long version;

    private Bank(UUID id, Cnpj cnpj, String code, String legalName, String tradeName, BankStatus status,
                 String statusReason, int requestLimitPerMinute, Integer dailyContractLimit,
                 Map<String, Object> processingSettings, List<BankContact> contacts, long version) {
        this.id = Objects.requireNonNull(id);
        this.cnpj = Objects.requireNonNull(cnpj);
        this.code = requireCode(code);
        this.legalName = requireName(legalName, "Razão social");
        this.tradeName = requireName(tradeName, "Nome fantasia");
        this.status = Objects.requireNonNull(status);
        this.statusReason = statusReason;
        this.requestLimitPerMinute = requireLimit(requestLimitPerMinute);
        this.dailyContractLimit = requireDailyLimit(dailyContractLimit);
        this.processingSettings = processingSettings == null ? Map.of() : Map.copyOf(processingSettings);
        this.contacts = requireContacts(contacts);
        this.version = version;
    }

    public static Bank create(String legalName, String tradeName, Cnpj cnpj, String code, Integer requestLimitPerMinute,
                              Integer dailyContractLimit, Map<String, Object> processingSettings, List<BankContact> contacts) {
        return new Bank(UuidV7.generate(), cnpj, normalizeCode(code), legalName, tradeName, BankStatus.ACTIVE, null,
                requestLimitPerMinute == null ? DEFAULT_REQUEST_LIMIT : requestLimitPerMinute, dailyContractLimit,
                processingSettings, contacts, 0);
    }

    public static Bank restore(UUID id, Cnpj cnpj, String code, String legalName, String tradeName, BankStatus status,
                               String statusReason, int requestLimitPerMinute, Integer dailyContractLimit,
                               Map<String, Object> processingSettings, List<BankContact> contacts, long version) {
        return new Bank(id, cnpj, code, legalName, tradeName, status, statusReason, requestLimitPerMinute,
                dailyContractLimit, processingSettings, contacts, version);
    }

    public void update(String legalName, String tradeName, int requestLimitPerMinute, Integer dailyContractLimit,
                       Map<String, Object> processingSettings) {
        this.legalName = requireName(legalName, "Razão social");
        this.tradeName = requireName(tradeName, "Nome fantasia");
        this.requestLimitPerMinute = requireLimit(requestLimitPerMinute);
        this.dailyContractLimit = requireDailyLimit(dailyContractLimit);
        this.processingSettings = processingSettings == null ? Map.of() : Map.copyOf(processingSettings);
    }

    /** Ativa, desativa ou suspende. Exige motivo; o histórico é imutável. */
    public StatusChange changeStatus(BankStatus target, String reason, UUID changedBy, Instant now) {
        Objects.requireNonNull(target);
        if (target == status) {
            throw new DomainException("status_unchanged", "O banco já está " + status);
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new DomainException("reason_required", "Informe o motivo (até 500 caracteres)");
        }
        StatusChange change = new StatusChange(id, status, target, reason.trim(), changedBy, now);
        status = target;
        statusReason = target == BankStatus.ACTIVE ? null : reason.trim();
        return change;
    }

    public void replaceContacts(List<BankContact> newContacts) {
        this.contacts = requireContacts(newContacts);
    }

    public boolean isActive() {
        return status == BankStatus.ACTIVE;
    }

    static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new DomainException("invalid_code", "Código: 2 a 30 caracteres entre A-Z, 0-9, _ e -");
        }
        return code;
    }

    private static String requireName(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new DomainException("invalid_name", field + " obrigatório, com até 200 caracteres");
        }
        return value.trim();
    }

    private static int requireLimit(int value) {
        if (value < 1 || value > 100_000) {
            throw new DomainException("invalid_limit", "Limite de requisições deve estar entre 1 e 100.000 por minuto");
        }
        return value;
    }

    private static Integer requireDailyLimit(Integer value) {
        if (value != null && value < 1) {
            throw new DomainException("invalid_limit", "Limite diário de contratos deve ser positivo (ou vazio para sem limite)");
        }
        return value;
    }

    private static List<BankContact> requireContacts(List<BankContact> contacts) {
        List<BankContact> list = contacts == null ? List.of() : List.copyOf(contacts);
        if (list.size() > MAX_CONTACTS) {
            throw new DomainException("too_many_contacts", "No máximo " + MAX_CONTACTS + " contatos");
        }
        Set<ContactArea> primaries = EnumSet.noneOf(ContactArea.class);
        for (BankContact contact : list) {
            if (contact.primary() && !primaries.add(contact.area())) {
                throw new DomainException("duplicate_primary_contact", "Só pode haver um contato principal na área " + contact.area());
            }
        }
        long distinctEmails = list.stream().map(c -> c.area() + "|" + c.email()).collect(Collectors.toSet()).size();
        if (distinctEmails != list.size()) {
            throw new DomainException("duplicate_contact", "Contato repetido na mesma área");
        }
        return list;
    }

    public UUID id() { return id; }
    public Cnpj cnpj() { return cnpj; }
    public String code() { return code; }
    public String legalName() { return legalName; }
    public String tradeName() { return tradeName; }
    public BankStatus status() { return status; }
    public String statusReason() { return statusReason; }
    public int requestLimitPerMinute() { return requestLimitPerMinute; }
    public Integer dailyContractLimit() { return dailyContractLimit; }
    public Map<String, Object> processingSettings() { return processingSettings; }
    public List<BankContact> contacts() { return contacts; }
    public long version() { return version; }
}
