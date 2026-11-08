package br.com.hubregistro.bank.domain;

import br.com.hubregistro.commons.error.DomainException;

import java.util.Locale;
import java.util.regex.Pattern;

/** Contato de uma área do banco; no máximo um principal por área. */
public record BankContact(String name, String email, String phone, ContactArea area, boolean primary) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{10,15}$");

    public BankContact {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new DomainException("invalid_contact", "Nome do contato obrigatório, com até 200 caracteres");
        }
        if (email == null || !EMAIL.matcher(email.trim()).matches() || email.length() > 254) {
            throw new DomainException("invalid_contact", "E-mail do contato inválido: " + email);
        }
        if (phone != null && !phone.isBlank() && !PHONE.matcher(phone.replaceAll("[\\s()\\-]", "")).matches()) {
            throw new DomainException("invalid_contact", "Telefone do contato inválido: " + phone);
        }
        if (area == null) {
            throw new DomainException("invalid_contact", "Área do contato obrigatória");
        }
        name = name.trim();
        email = email.trim().toLowerCase(Locale.ROOT);
        phone = phone == null || phone.isBlank() ? null : phone.replaceAll("[\\s()\\-]", "");
    }
}
