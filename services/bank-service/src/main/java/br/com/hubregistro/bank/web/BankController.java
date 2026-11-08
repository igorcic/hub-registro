package br.com.hubregistro.bank.web;

import br.com.hubregistro.bank.application.BankService;
import br.com.hubregistro.bank.application.BankService.BankData;
import br.com.hubregistro.bank.domain.BankRepository.BankPage;
import br.com.hubregistro.bank.domain.BankStatus;
import br.com.hubregistro.bank.web.BankDtos.BankResponse;
import br.com.hubregistro.bank.web.BankDtos.ChangeStatusRequest;
import br.com.hubregistro.bank.web.BankDtos.ContactDto;
import br.com.hubregistro.bank.web.BankDtos.CreateBankRequest;
import br.com.hubregistro.bank.web.BankDtos.PageResponse;
import br.com.hubregistro.bank.web.BankDtos.ReplaceContactsRequest;
import br.com.hubregistro.bank.web.BankDtos.StatusChangeResponse;
import br.com.hubregistro.bank.web.BankDtos.UpdateBankRequest;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/banks")
class BankController {

    private final BankService service;

    BankController(BankService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('bank:read')")
    PageResponse<BankResponse> list(@RequestParam(required = false) BankStatus status,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        BankPage result = service.search(HubSecurity.requirePrincipal(), status, search, page, size);
        return new PageResponse<>(result.items().stream().map(BankResponse::of).toList(), result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('bank:read')")
    BankResponse get(@PathVariable UUID id) {
        return BankResponse.of(service.get(HubSecurity.requirePrincipal(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('bank:manage')")
    BankResponse create(@Valid @RequestBody CreateBankRequest body) {
        BankData data = new BankData(body.legalName(), body.tradeName(), body.cnpj(), body.code(),
                body.requestLimitPerMinute(), body.dailyContractLimit(), body.processingSettings(),
                body.contacts() == null ? List.of() : body.contacts().stream().map(ContactDto::toDomain).toList());
        return BankResponse.of(service.create(HubSecurity.requirePrincipal(), data));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('bank:manage')")
    BankResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateBankRequest body) {
        BankData data = new BankData(body.legalName(), body.tradeName(), null, null, body.requestLimitPerMinute(),
                body.dailyContractLimit(), body.processingSettings(), null);
        return BankResponse.of(service.update(HubSecurity.requirePrincipal(), id, data));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('bank:manage')")
    BankResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest body) {
        return BankResponse.of(service.changeStatus(HubSecurity.requirePrincipal(), id, body.status(), body.reason()));
    }

    @PutMapping("/{id}/contacts")
    @PreAuthorize("hasAuthority('bank:manage') or hasRole('BANK_ADMIN')")
    BankResponse replaceContacts(@PathVariable UUID id, @Valid @RequestBody ReplaceContactsRequest body) {
        return BankResponse.of(service.replaceContacts(HubSecurity.requirePrincipal(), id,
                body.contacts().stream().map(ContactDto::toDomain).toList()));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAuthority('bank:read')")
    List<StatusChangeResponse> statusHistory(@PathVariable UUID id) {
        return service.statusHistory(HubSecurity.requirePrincipal(), id).stream().map(StatusChangeResponse::of).toList();
    }
}
