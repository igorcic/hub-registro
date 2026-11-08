package br.com.hubregistro.bank.web;

import br.com.hubregistro.bank.domain.Bank;
import br.com.hubregistro.bank.domain.BankContact;
import br.com.hubregistro.bank.domain.BankStatus;
import br.com.hubregistro.bank.domain.ContactArea;
import br.com.hubregistro.bank.domain.StatusChange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BankDtos {

    private BankDtos() {
    }

    public record ContactDto(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 254) String email,
                             @Size(max = 20) String phone, @NotNull ContactArea area, boolean primary) {
        BankContact toDomain() {
            return new BankContact(name, email, phone, area, primary);
        }

        static ContactDto of(BankContact c) {
            return new ContactDto(c.name(), c.email(), c.phone(), c.area(), c.primary());
        }
    }

    public record CreateBankRequest(@NotBlank @Size(max = 200) String legalName,
                                    @NotBlank @Size(max = 200) String tradeName,
                                    @NotBlank @Size(max = 18) String cnpj,
                                    @NotBlank @Size(max = 30) String code,
                                    @Min(1) @Max(100000) Integer requestLimitPerMinute,
                                    @Min(1) Integer dailyContractLimit,
                                    Map<String, Object> processingSettings,
                                    @Size(max = 20) List<@Valid ContactDto> contacts) {
    }

    public record UpdateBankRequest(@NotBlank @Size(max = 200) String legalName,
                                    @NotBlank @Size(max = 200) String tradeName,
                                    @Min(1) @Max(100000) Integer requestLimitPerMinute,
                                    @Min(1) Integer dailyContractLimit,
                                    Map<String, Object> processingSettings) {
    }

    public record ChangeStatusRequest(@NotNull BankStatus status, @NotBlank @Size(max = 500) String reason) {
    }

    public record ReplaceContactsRequest(@NotNull @Size(max = 20) List<@Valid ContactDto> contacts) {
    }

    public record BankResponse(UUID id, String legalName, String tradeName, String cnpj, String cnpjFormatted, String code,
                               BankStatus status, String statusReason, int requestLimitPerMinute,
                               Integer dailyContractLimit, Map<String, Object> processingSettings,
                               List<ContactDto> contacts) {
        static BankResponse of(Bank b) {
            return new BankResponse(b.id(), b.legalName(), b.tradeName(), b.cnpj().value(), b.cnpj().formatted(), b.code(),
                    b.status(), b.statusReason(), b.requestLimitPerMinute(), b.dailyContractLimit(), b.processingSettings(),
                    b.contacts().stream().map(ContactDto::of).toList());
        }
    }

    public record PageResponse<T>(List<T> items, long total, int page, int size) {
    }

    public record StatusChangeResponse(BankStatus from, BankStatus to, String reason, UUID changedBy, Instant changedAt) {
        static StatusChangeResponse of(StatusChange c) {
            return new StatusChangeResponse(c.from(), c.to(), c.reason(), c.changedBy(), c.changedAt());
        }
    }
}
