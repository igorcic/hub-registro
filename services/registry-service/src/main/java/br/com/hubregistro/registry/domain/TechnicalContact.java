package br.com.hubregistro.registry.domain;

import br.com.hubregistro.commons.error.DomainException;

import java.util.Locale;

public record TechnicalContact(String name, String email, String phone, String jobTitle, boolean primary) {

    public TechnicalContact {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new DomainException("invalid_contact", "Nome do responsável técnico obrigatório");
        }
        if (email == null || !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new DomainException("invalid_contact", "E-mail do responsável técnico inválido: " + email);
        }
        name = name.trim();
        email = email.trim().toLowerCase(Locale.ROOT);
        phone = phone == null || phone.isBlank() ? null : phone.replaceAll("[\\s()\\-]", "");
        if (phone != null && !phone.matches("^\\+?[0-9]{10,15}$")) {
            throw new DomainException("invalid_contact", "Telefone inválido: " + phone);
        }
        jobTitle = jobTitle == null || jobTitle.isBlank() ? null : jobTitle.trim();
    }
}
