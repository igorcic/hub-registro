package br.com.hubregistro.registry.domain;

import br.com.hubregistro.commons.document.Cnpj;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.id.UuidV7;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Empresa registradora: quem é, quais tipos de contrato atende e onde.
 * Nasce inativa; só é ativada com ao menos um tipo de contrato e uma região de atendimento,
 * porque sem isso o roteamento não teria como escolhê-la.
 */
public class Registry {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{2,30}$");
    private static final Pattern CONTRACT_TYPE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    private final UUID id;
    private final Cnpj cnpj;
    private final String code;
    private String legalName;
    private String tradeName;
    private Address address;
    private Integer dailyCapacity;
    private RegistryStatus status;
    private String statusReason;
    private Set<String> contractTypes;
    private Set<ServiceArea> serviceAreas;
    private List<TechnicalContact> technicalContacts;
    private long version;

    private Registry(UUID id, Cnpj cnpj, String code, String legalName, String tradeName, Address address,
                     Integer dailyCapacity, RegistryStatus status, String statusReason, Set<String> contractTypes,
                     Set<ServiceArea> serviceAreas, List<TechnicalContact> technicalContacts, long version) {
        this.id = Objects.requireNonNull(id);
        this.cnpj = Objects.requireNonNull(cnpj);
        if (code == null || !CODE.matcher(code).matches()) {
            throw new DomainException("invalid_code", "Código: 2 a 30 caracteres entre A-Z, 0-9, _ e -");
        }
        this.code = code;
        this.legalName = requireName(legalName, "Razão social");
        this.tradeName = requireName(tradeName, "Nome fantasia");
        this.address = Objects.requireNonNull(address, "Endereço obrigatório");
        this.dailyCapacity = requireCapacity(dailyCapacity);
        this.status = Objects.requireNonNull(status);
        this.statusReason = statusReason;
        this.contractTypes = requireContractTypes(contractTypes);
        this.serviceAreas = serviceAreas == null ? Set.of() : Set.copyOf(serviceAreas);
        this.technicalContacts = requireContacts(technicalContacts);
        this.version = version;
    }

    public static Registry create(String legalName, String tradeName, Cnpj cnpj, String code, Address address,
                                  Integer dailyCapacity, Set<String> contractTypes, Set<ServiceArea> serviceAreas,
                                  List<TechnicalContact> technicalContacts) {
        return new Registry(UuidV7.generate(), cnpj, code == null ? null : code.trim().toUpperCase(Locale.ROOT),
                legalName, tradeName, address, dailyCapacity, RegistryStatus.INACTIVE, "Aguardando ativação",
                contractTypes, serviceAreas, technicalContacts, 0);
    }

    public static Registry restore(UUID id, Cnpj cnpj, String code, String legalName, String tradeName, Address address,
                                   Integer dailyCapacity, RegistryStatus status, String statusReason, Set<String> contractTypes,
                                   Set<ServiceArea> serviceAreas, List<TechnicalContact> technicalContacts, long version) {
        return new Registry(id, cnpj, code, legalName, tradeName, address, dailyCapacity, status, statusReason,
                contractTypes, serviceAreas, technicalContacts, version);
    }

    public void update(String legalName, String tradeName, Address address, Integer dailyCapacity) {
        this.legalName = requireName(legalName, "Razão social");
        this.tradeName = requireName(tradeName, "Nome fantasia");
        this.address = Objects.requireNonNull(address, "Endereço obrigatório");
        this.dailyCapacity = requireCapacity(dailyCapacity);
    }

    /** Define o que a registradora atende. Uma registradora ativa não pode ficar sem tipos nem regiões. */
    public void defineCoverage(Set<String> contractTypes, Set<ServiceArea> serviceAreas) {
        Set<String> types = requireContractTypes(contractTypes);
        Set<ServiceArea> areas = serviceAreas == null ? Set.of() : Set.copyOf(serviceAreas);
        if (status == RegistryStatus.ACTIVE && (types.isEmpty() || areas.isEmpty())) {
            throw new DomainException("coverage_required", "Registradora ativa precisa de ao menos um tipo de contrato e uma região");
        }
        this.contractTypes = types;
        this.serviceAreas = areas;
    }

    public void replaceTechnicalContacts(List<TechnicalContact> contacts) {
        this.technicalContacts = requireContacts(contacts);
    }

    public StatusChange changeStatus(RegistryStatus target, String reason, UUID changedBy, Instant now) {
        Objects.requireNonNull(target);
        if (target == status) {
            throw new DomainException("status_unchanged", "A registradora já está " + status);
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new DomainException("reason_required", "Informe o motivo (até 500 caracteres)");
        }
        if (target == RegistryStatus.ACTIVE && (contractTypes.isEmpty() || serviceAreas.isEmpty())) {
            throw new DomainException("coverage_required",
                    "Para ativar, cadastre ao menos um tipo de contrato atendido e uma região de atendimento");
        }
        StatusChange change = new StatusChange(id, status, target, reason.trim(), changedBy, now);
        status = target;
        statusReason = target == RegistryStatus.ACTIVE ? null : reason.trim();
        return change;
    }

    /** A registradora atende o município (diretamente ou pela UF inteira)? */
    public boolean serves(String uf, String ibgeCode) {
        return serviceAreas.stream().anyMatch(a -> a.uf().equals(uf) && (a.wholeState() || a.ibgeCode().equals(ibgeCode)));
    }

    private static String requireName(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new DomainException("invalid_name", field + " obrigatório, com até 200 caracteres");
        }
        return value.trim();
    }

    private static Integer requireCapacity(Integer value) {
        if (value != null && value < 1) {
            throw new DomainException("invalid_capacity", "Capacidade diária deve ser positiva (ou vazia para sem limite)");
        }
        return value;
    }

    private static Set<String> requireContractTypes(Set<String> types) {
        if (types == null) {
            return Set.of();
        }
        Set<String> normalized = types.stream().map(t -> t == null ? "" : t.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        for (String type : normalized) {
            if (!CONTRACT_TYPE.matcher(type).matches()) {
                throw new DomainException("invalid_contract_type", "Tipo de contrato inválido: " + type);
            }
        }
        return normalized;
    }

    private static List<TechnicalContact> requireContacts(List<TechnicalContact> contacts) {
        List<TechnicalContact> list = contacts == null ? List.of() : List.copyOf(contacts);
        if (list.size() > 20) {
            throw new DomainException("too_many_contacts", "No máximo 20 responsáveis técnicos");
        }
        if (list.stream().filter(TechnicalContact::primary).count() > 1) {
            throw new DomainException("duplicate_primary_contact", "Só pode haver um responsável técnico principal");
        }
        return list;
    }

    public UUID id() { return id; }
    public Cnpj cnpj() { return cnpj; }
    public String code() { return code; }
    public String legalName() { return legalName; }
    public String tradeName() { return tradeName; }
    public Address address() { return address; }
    public Integer dailyCapacity() { return dailyCapacity; }
    public RegistryStatus status() { return status; }
    public String statusReason() { return statusReason; }
    public Set<String> contractTypes() { return contractTypes; }
    public Set<ServiceArea> serviceAreas() { return serviceAreas; }
    public List<TechnicalContact> technicalContacts() { return technicalContacts; }
    public long version() { return version; }
}
