package br.com.hubregistro.registry.web;

import br.com.hubregistro.registry.domain.Address;
import br.com.hubregistro.registry.domain.Registry;
import br.com.hubregistro.registry.domain.RegistryStatus;
import br.com.hubregistro.registry.domain.ServiceArea;
import br.com.hubregistro.registry.domain.StatusChange;
import br.com.hubregistro.registry.domain.TechnicalContact;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class RegistryDtos {

    private RegistryDtos() {
    }

    public record AddressDto(@NotBlank String street, @NotBlank String number, String complement, @NotBlank String district,
                             @NotBlank String city, @NotBlank String ibgeCode, @NotBlank String uf, @NotBlank String zipCode) {
        Address toDomain() {
            return new Address(street, number, complement, district, city, ibgeCode, uf, zipCode);
        }

        static AddressDto of(Address a) {
            return new AddressDto(a.street(), a.number(), a.complement(), a.district(), a.city(), a.ibgeCode(), a.uf(), a.zipCode());
        }
    }

    public record ServiceAreaDto(@NotBlank String uf, String ibgeCode) {
        ServiceArea toDomain() {
            return new ServiceArea(uf, ibgeCode);
        }

        static ServiceAreaDto of(ServiceArea a) {
            return new ServiceAreaDto(a.uf(), a.ibgeCode());
        }
    }

    public record TechnicalContactDto(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 254) String email,
                                      @Size(max = 20) String phone, @Size(max = 100) String jobTitle, boolean primary) {
        TechnicalContact toDomain() {
            return new TechnicalContact(name, email, phone, jobTitle, primary);
        }

        static TechnicalContactDto of(TechnicalContact c) {
            return new TechnicalContactDto(c.name(), c.email(), c.phone(), c.jobTitle(), c.primary());
        }
    }

    public record CreateRegistryRequest(@NotBlank @Size(max = 200) String legalName, @NotBlank @Size(max = 200) String tradeName,
                                        @NotBlank @Size(max = 18) String cnpj, @NotBlank @Size(max = 30) String code,
                                        @NotNull @Valid AddressDto address, @Min(1) Integer dailyCapacity,
                                        Set<@NotBlank String> contractTypes, @Size(max = 6000) List<@Valid ServiceAreaDto> serviceAreas,
                                        @Size(max = 20) List<@Valid TechnicalContactDto> technicalContacts) {
    }

    public record UpdateRegistryRequest(@NotBlank @Size(max = 200) String legalName, @NotBlank @Size(max = 200) String tradeName,
                                        @NotNull @Valid AddressDto address, @Min(1) Integer dailyCapacity) {
    }

    public record CoverageRequest(@NotNull Set<@NotBlank String> contractTypes,
                                  @NotNull @Size(max = 6000) List<@Valid ServiceAreaDto> serviceAreas) {
        Set<ServiceArea> areas() {
            return serviceAreas.stream().map(ServiceAreaDto::toDomain).collect(Collectors.toSet());
        }
    }

    public record TechnicalContactsRequest(@NotNull @Size(max = 20) List<@Valid TechnicalContactDto> technicalContacts) {
    }

    public record ChangeStatusRequest(@NotNull RegistryStatus status, @NotBlank @Size(max = 500) String reason) {
    }

    public record RegistryResponse(UUID id, String legalName, String tradeName, String cnpj, String cnpjFormatted, String code,
                                   AddressDto address, Integer dailyCapacity, RegistryStatus status, String statusReason,
                                   List<String> contractTypes, List<ServiceAreaDto> serviceAreas,
                                   List<TechnicalContactDto> technicalContacts) {
        static RegistryResponse of(Registry r) {
            return new RegistryResponse(r.id(), r.legalName(), r.tradeName(), r.cnpj().value(), r.cnpj().formatted(), r.code(),
                    AddressDto.of(r.address()), r.dailyCapacity(), r.status(), r.statusReason(),
                    r.contractTypes().stream().sorted().toList(),
                    r.serviceAreas().stream()
                            .sorted(Comparator.comparing(ServiceArea::uf).thenComparing(a -> a.ibgeCode() == null ? "" : a.ibgeCode()))
                            .map(ServiceAreaDto::of).toList(),
                    r.technicalContacts().stream().map(TechnicalContactDto::of).toList());
        }
    }

    public record PageResponse<T>(List<T> items, long total, int page, int size) {
    }

    public record StatusChangeResponse(RegistryStatus from, RegistryStatus to, String reason, UUID changedBy, Instant changedAt) {
        static StatusChangeResponse of(StatusChange c) {
            return new StatusChangeResponse(c.from(), c.to(), c.reason(), c.changedBy(), c.changedAt());
        }
    }
}
