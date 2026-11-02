package br.com.hubregistro.bank;

import br.com.hubregistro.test.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class BankDatabaseRulesIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void acceptsNumericAndAlphanumericCnpj() {
        insertBank("11222333000181", "BANCO_NUM");
        insertBank("12ABC34501DE35", "BANCO_ALFA");
    }

    @Test
    void rejectsMalformedCnpj() {
        // 14 caracteres, mas dígitos verificadores não numéricos
        assertThatThrownBy(() -> insertBank("112223330001AB", "BANCO_X"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // minúsculas não são aceitas: a aplicação normaliza antes de gravar
        assertThatThrownBy(() -> insertBank("12abc34501de35", "BANCO_Y"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cnpjIsUniqueAmongActiveBanksButCanBeReusedAfterSoftDelete() {
        insertBank("55666777000190", "BANCO_A");
        assertThatThrownBy(() -> insertBank("55666777000190", "BANCO_B"))
                .isInstanceOf(DuplicateKeyException.class);

        jdbc.update("UPDATE banks SET deleted_at = now() WHERE code = 'BANCO_A'");
        insertBank("55666777000190", "BANCO_C");
    }

    private void insertBank(String cnpj, String code) {
        jdbc.update("INSERT INTO banks (legal_name, trade_name, cnpj, code) VALUES (?, ?, ?, ?)",
                "Banco " + code + " S.A.", "Banco " + code, cnpj, code);
    }
}
