package br.com.hubregistro.registry.web;

import br.com.hubregistro.registry.application.RegistryService;
import br.com.hubregistro.registry.application.RegistryService.RegistryData;
import br.com.hubregistro.registry.domain.RegistryRepository.RegistryPage;
import br.com.hubregistro.registry.domain.RegistryStatus;
import br.com.hubregistro.registry.web.RegistryDtos.ChangeStatusRequest;
import br.com.hubregistro.registry.web.RegistryDtos.CoverageRequest;
import br.com.hubregistro.registry.web.RegistryDtos.CreateRegistryRequest;
import br.com.hubregistro.registry.web.RegistryDtos.PageResponse;
import br.com.hubregistro.registry.web.RegistryDtos.RegistryResponse;
import br.com.hubregistro.registry.web.RegistryDtos.ServiceAreaDto;
import br.com.hubregistro.registry.web.RegistryDtos.StatusChangeResponse;
import br.com.hubregistro.registry.web.RegistryDtos.TechnicalContactDto;
import br.com.hubregistro.registry.web.RegistryDtos.TechnicalContactsRequest;
import br.com.hubregistro.registry.web.RegistryDtos.UpdateRegistryRequest;
import br.com.hubregistro.security.HubSecurity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/registries")
class RegistryController {

    private final RegistryService service;

    RegistryController(RegistryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('registry:read')")
    PageResponse<RegistryResponse> list(@RequestParam(required = false) RegistryStatus status,
                                        @RequestParam(required = false) String contractType,
                                        @RequestParam(required = false) String uf,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        RegistryPage result = service.search(HubSecurity.requirePrincipal(), status, contractType, uf, search, page, size);
        return new PageResponse<>(result.items().stream().map(RegistryResponse::of).toList(), result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('registry:read')")
    RegistryResponse get(@PathVariable UUID id) {
        return RegistryResponse.of(service.get(HubSecurity.requirePrincipal(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('registry:manage')")
    RegistryResponse create(@Valid @RequestBody CreateRegistryRequest b) {
        Set<String> types = b.contractTypes() == null ? Set.of() : b.contractTypes();
        RegistryData data = new RegistryData(b.legalName(), b.tradeName(), b.cnpj(), b.code(), b.address().toDomain(),
                b.dailyCapacity(), types,
                b.serviceAreas() == null ? Set.of() : b.serviceAreas().stream().map(ServiceAreaDto::toDomain).collect(Collectors.toSet()),
                b.technicalContacts() == null ? List.of() : b.technicalContacts().stream().map(TechnicalContactDto::toDomain).toList());
        return RegistryResponse.of(service.create(HubSecurity.requirePrincipal(), data));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('registry:manage')")
    RegistryResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRegistryRequest b) {
        return RegistryResponse.of(service.update(HubSecurity.requirePrincipal(), id, b.legalName(), b.tradeName(),
                b.address().toDomain(), b.dailyCapacity()));
    }

    @PutMapping("/{id}/coverage")
    @PreAuthorize("hasAuthority('registry:manage')")
    RegistryResponse defineCoverage(@PathVariable UUID id, @Valid @RequestBody CoverageRequest b) {
        return RegistryResponse.of(service.defineCoverage(HubSecurity.requirePrincipal(), id, b.contractTypes(), b.areas()));
    }

    @PutMapping("/{id}/technical-contacts")
    @PreAuthorize("hasAuthority('registry:manage') or hasRole('REGISTRY_ADMIN')")
    RegistryResponse replaceContacts(@PathVariable UUID id, @Valid @RequestBody TechnicalContactsRequest b) {
        return RegistryResponse.of(service.replaceTechnicalContacts(HubSecurity.requirePrincipal(), id,
                b.technicalContacts().stream().map(TechnicalContactDto::toDomain).toList()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('registry:manage')")
    RegistryResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest b) {
        return RegistryResponse.of(service.changeStatus(HubSecurity.requirePrincipal(), id, b.status(), b.reason()));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAuthority('registry:read')")
    List<StatusChangeResponse> statusHistory(@PathVariable UUID id) {
        return service.statusHistory(HubSecurity.requirePrincipal(), id).stream().map(StatusChangeResponse::of).toList();
    }
}
