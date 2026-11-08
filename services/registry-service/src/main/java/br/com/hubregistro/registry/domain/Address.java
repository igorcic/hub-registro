package br.com.hubregistro.registry.domain;

import br.com.hubregistro.commons.error.DomainException;

import java.util.Locale;
import java.util.Set;

public record Address(String street, String number, String complement, String district, String city, String ibgeCode,
                      String uf, String zipCode) {

    static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG",
            "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    public Address {
        street = required(street, "Logradouro", 200);
        number = required(number, "Número", 20);
        complement = complement == null || complement.isBlank() ? null : complement.trim();
        district = required(district, "Bairro", 100);
        city = required(city, "Cidade", 100);
        ibgeCode = ibgeCode == null ? null : ibgeCode.trim();
        if (ibgeCode == null || !ibgeCode.matches("\\d{7}")) {
            throw new DomainException("invalid_address", "Código IBGE do município deve ter 7 dígitos");
        }
        uf = uf == null ? null : uf.trim().toUpperCase(Locale.ROOT);
        if (!UFS.contains(uf)) {
            throw new DomainException("invalid_address", "UF inválida: " + uf);
        }
        zipCode = zipCode == null ? null : zipCode.replaceAll("\\D", "");
        if (zipCode == null || zipCode.length() != 8) {
            throw new DomainException("invalid_address", "CEP deve ter 8 dígitos");
        }
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new DomainException("invalid_address", field + " obrigatório, com até " + max + " caracteres");
        }
        return value.trim();
    }
}
