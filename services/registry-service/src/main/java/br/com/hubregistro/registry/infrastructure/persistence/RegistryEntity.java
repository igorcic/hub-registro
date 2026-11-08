package br.com.hubregistro.registry.infrastructure.persistence;

import br.com.hubregistro.registry.domain.Address;
import br.com.hubregistro.registry.domain.Registry;
import br.com.hubregistro.registry.domain.RegistryStatus;
import br.com.hubregistro.registry.domain.ServiceArea;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "registries")
public class RegistryEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trade_name", nullable = false)
    private String tradeName;

    @Column(nullable = false, updatable = false, columnDefinition = "char(14)")
    private String cnpj;

    @Column(nullable = false, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistryStatus status;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "daily_capacity")
    private Integer dailyCapacity;

    private String street;
    private String number;
    private String complement;
    private String district;
    private String city;

    @Column(name = "ibge_code", columnDefinition = "char(7)")
    private String ibgeCode;

    @Column(columnDefinition = "char(2)")
    private String uf;

    @Column(name = "zip_code", columnDefinition = "char(8)")
    private String zipCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "registry_contract_types", joinColumns = @JoinColumn(name = "registry_id"))
    @Column(name = "contract_type")
    private Set<String> contractTypes = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "registry_service_areas", joinColumns = @JoinColumn(name = "registry_id"))
    private Set<ServiceAreaEmbeddable> serviceAreas = new HashSet<>();

    protected RegistryEntity() {
    }

    static RegistryEntity newFrom(Registry registry) {
        RegistryEntity entity = new RegistryEntity();
        entity.id = registry.id();
        entity.cnpj = registry.cnpj().value();
        entity.code = registry.code();
        entity.copyFrom(registry);
        return entity;
    }

    void copyFrom(Registry registry) {
        legalName = registry.legalName();
        tradeName = registry.tradeName();
        status = registry.status();
        statusReason = registry.statusReason();
        dailyCapacity = registry.dailyCapacity();
        Address a = registry.address();
        street = a.street();
        number = a.number();
        complement = a.complement();
        district = a.district();
        city = a.city();
        ibgeCode = a.ibgeCode();
        uf = a.uf();
        zipCode = a.zipCode();
        if (!contractTypes.equals(registry.contractTypes())) {
            contractTypes.clear();
            contractTypes.addAll(registry.contractTypes());
        }
        Set<ServiceAreaEmbeddable> areas = registry.serviceAreas().stream().map(ServiceAreaEmbeddable::of).collect(Collectors.toSet());
        if (!serviceAreas.equals(areas)) {
            serviceAreas.clear();
            serviceAreas.addAll(areas);
        }
        updatedAt = Instant.now();
    }

    Address address() {
        return new Address(street, number, complement, district, city, ibgeCode == null ? null : ibgeCode.trim(),
                uf == null ? null : uf.trim(), zipCode == null ? null : zipCode.trim());
    }

    Set<ServiceArea> serviceAreas() {
        return serviceAreas.stream().map(ServiceAreaEmbeddable::toDomain).collect(Collectors.toUnmodifiableSet());
    }

    @PrePersist
    void prePersist() {
        createdAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return version == null; }

    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getCnpj() { return cnpj; }
    public String getCode() { return code; }
    public RegistryStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public Integer getDailyCapacity() { return dailyCapacity; }
    public Set<String> getContractTypes() { return contractTypes; }
    public Instant getDeletedAt() { return deletedAt; }
    public Long getVersion() { return version; }
}
