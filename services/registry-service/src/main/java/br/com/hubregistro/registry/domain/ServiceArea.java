package br.com.hubregistro.registry.domain;

import br.com.hubregistro.commons.error.DomainException;

import java.util.Locale;

/** Região atendida: uma UF inteira ({@code ibgeCode} nulo) ou um município. */
public record ServiceArea(String uf, String ibgeCode) {

    public ServiceArea {
        uf = uf == null ? null : uf.trim().toUpperCase(Locale.ROOT);
        if (!Address.UFS.contains(uf)) {
            throw new DomainException("invalid_service_area", "UF inválida: " + uf);
        }
        ibgeCode = ibgeCode == null || ibgeCode.isBlank() ? null : ibgeCode.trim();
        if (ibgeCode != null && !ibgeCode.matches("\\d{7}")) {
            throw new DomainException("invalid_service_area", "Código IBGE deve ter 7 dígitos: " + ibgeCode);
        }
    }

    public boolean wholeState() {
        return ibgeCode == null;
    }
}
