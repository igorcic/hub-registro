package br.com.hubregistro.registry.infrastructure.persistence;

import br.com.hubregistro.registry.domain.ServiceArea;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class ServiceAreaEmbeddable {

    @Column(nullable = false, columnDefinition = "char(2)")
    private String uf;

    @Column(name = "ibge_code", columnDefinition = "char(7)")
    private String ibgeCode;

    protected ServiceAreaEmbeddable() {
    }

    static ServiceAreaEmbeddable of(ServiceArea area) {
        ServiceAreaEmbeddable e = new ServiceAreaEmbeddable();
        e.uf = area.uf();
        e.ibgeCode = area.ibgeCode();
        return e;
    }

    ServiceArea toDomain() {
        return new ServiceArea(uf.trim(), ibgeCode == null ? null : ibgeCode.trim());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ServiceAreaEmbeddable other && Objects.equals(uf, other.uf) && Objects.equals(ibgeCode, other.ibgeCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uf, ibgeCode);
    }
}
