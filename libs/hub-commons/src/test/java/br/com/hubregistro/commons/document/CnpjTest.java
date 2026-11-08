package br.com.hubregistro.commons.document;

import br.com.hubregistro.commons.error.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CnpjTest {

    @Test
    void acceptsNumericCnpjWithOrWithoutPunctuation() {
        assertThat(new Cnpj("11.222.333/0001-81").value()).isEqualTo("11222333000181");
        assertThat(new Cnpj("11222333000181").formatted()).isEqualTo("11.222.333/0001-81");
    }

    @Test
    void acceptsAlphanumericCnpjFromTheFederalRevenueExample() {
        Cnpj cnpj = new Cnpj("12.abc.345/01de-35");

        assertThat(cnpj.value()).isEqualTo("12ABC34501DE35");
        assertThat(cnpj.formatted()).isEqualTo("12.ABC.345/01DE-35");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11222333000182", "12ABC34501DE36", "11111111111111", "1122233300018", "11222333000AB1", ""})
    void rejectsWrongCheckDigitsAndMalformedValues(String value) {
        assertThatThrownBy(() -> new Cnpj(value)).isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", "invalid_cnpj");
    }
}
