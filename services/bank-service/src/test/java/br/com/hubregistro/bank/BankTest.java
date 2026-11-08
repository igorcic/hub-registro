package br.com.hubregistro.bank;

import br.com.hubregistro.bank.domain.Bank;
import br.com.hubregistro.bank.domain.BankContact;
import br.com.hubregistro.bank.domain.BankStatus;
import br.com.hubregistro.bank.domain.ContactArea;
import br.com.hubregistro.bank.domain.StatusChange;
import br.com.hubregistro.commons.document.Cnpj;
import br.com.hubregistro.commons.error.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankTest {

    private static Bank newBank(List<BankContact> contacts) {
        return Bank.create("Banco Alfa S.A.", "Banco Alfa", new Cnpj("11.222.333/0001-81"), " banco_alfa ", null, null, null, contacts);
    }

    @Test
    void newBankIsActiveWithNormalizedCodeAndDefaultLimit() {
        Bank bank = newBank(List.of());

        assertThat(bank.status()).isEqualTo(BankStatus.ACTIVE);
        assertThat(bank.code()).isEqualTo("BANCO_ALFA");
        assertThat(bank.requestLimitPerMinute()).isEqualTo(600);
    }

    @Test
    void statusChangeRequiresReasonAndADifferentStatus() {
        Bank bank = newBank(List.of());

        assertThatThrownBy(() -> bank.changeStatus(BankStatus.SUSPENDED, " ", UUID.randomUUID(), Instant.now()))
                .hasFieldOrPropertyWithValue("code", "reason_required");
        assertThatThrownBy(() -> bank.changeStatus(BankStatus.ACTIVE, "x", UUID.randomUUID(), Instant.now()))
                .hasFieldOrPropertyWithValue("code", "status_unchanged");

        StatusChange change = bank.changeStatus(BankStatus.SUSPENDED, "Pendência cadastral", UUID.randomUUID(), Instant.now());
        assertThat(change.from()).isEqualTo(BankStatus.ACTIVE);
        assertThat(bank.statusReason()).isEqualTo("Pendência cadastral");

        bank.changeStatus(BankStatus.ACTIVE, "Regularizado", UUID.randomUUID(), Instant.now());
        assertThat(bank.statusReason()).isNull();
    }

    @Test
    void onlyOnePrimaryContactPerArea() {
        List<BankContact> contacts = List.of(
                new BankContact("Ana", "ana@alfa.dev", null, ContactArea.TECHNICAL, true),
                new BankContact("Beto", "beto@alfa.dev", null, ContactArea.TECHNICAL, true));

        assertThatThrownBy(() -> newBank(contacts)).hasFieldOrPropertyWithValue("code", "duplicate_primary_contact");
    }

    @Test
    void contactValidationNormalizesEmailAndPhone() {
        BankContact contact = new BankContact(" Ana ", "Ana@Alfa.DEV", "(11) 99999-0001", ContactArea.BUSINESS, false);

        assertThat(contact.email()).isEqualTo("ana@alfa.dev");
        assertThat(contact.phone()).isEqualTo("11999990001");
        assertThatThrownBy(() -> new BankContact("A", "sem-arroba", null, ContactArea.BUSINESS, false))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsInvalidCodeAndLimits() {
        assertThatThrownBy(() -> Bank.create("A", "A", new Cnpj("11222333000181"), "x", null, null, null, List.of()))
                .hasFieldOrPropertyWithValue("code", "invalid_code");
        assertThatThrownBy(() -> Bank.create("A", "A", new Cnpj("11222333000181"), "AB", 0, null, null, List.of()))
                .hasFieldOrPropertyWithValue("code", "invalid_limit");
    }
}
