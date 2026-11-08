package br.com.hubregistro.bank.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface BankJpaRepository extends JpaRepository<BankEntity, UUID>, JpaSpecificationExecutor<BankEntity> {

    @Query("select count(b) > 0 from BankEntity b where b.cnpj = :cnpj and b.deletedAt is null")
    boolean existsActiveByCnpj(@Param("cnpj") String cnpj);

    @Query("select count(b) > 0 from BankEntity b where b.code = :code and b.deletedAt is null")
    boolean existsActiveByCode(@Param("code") String code);
}
