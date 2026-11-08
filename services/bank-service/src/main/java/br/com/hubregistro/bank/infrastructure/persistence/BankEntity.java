package br.com.hubregistro.bank.infrastructure.persistence;

import br.com.hubregistro.bank.domain.Bank;
import br.com.hubregistro.bank.domain.BankStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "banks")
public class BankEntity implements Persistable<UUID> {

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
    private BankStatus status;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "request_limit_per_minute", nullable = false)
    private int requestLimitPerMinute;

    @Column(name = "daily_contract_limit")
    private Integer dailyContractLimit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processing_settings", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> processingSettings = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    protected BankEntity() {
    }

    static BankEntity newFrom(Bank bank, UUID actorId) {
        BankEntity entity = new BankEntity();
        entity.id = bank.id();
        entity.cnpj = bank.cnpj().value();
        entity.code = bank.code();
        entity.createdBy = actorId;
        entity.copyFrom(bank, actorId);
        return entity;
    }

    /** Sempre atualiza updated_at: qualquer gravação incrementa a versão (ordem dos eventos). */
    void copyFrom(Bank bank, UUID actorId) {
        legalName = bank.legalName();
        tradeName = bank.tradeName();
        status = bank.status();
        statusReason = bank.statusReason();
        requestLimitPerMinute = bank.requestLimitPerMinute();
        dailyContractLimit = bank.dailyContractLimit();
        processingSettings = new HashMap<>(bank.processingSettings());
        updatedAt = Instant.now();
        updatedBy = actorId;
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
    public BankStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public int getRequestLimitPerMinute() { return requestLimitPerMinute; }
    public Integer getDailyContractLimit() { return dailyContractLimit; }
    public Map<String, Object> getProcessingSettings() { return processingSettings; }
    public Long getVersion() { return version; }
    public Instant getDeletedAt() { return deletedAt; }
}
