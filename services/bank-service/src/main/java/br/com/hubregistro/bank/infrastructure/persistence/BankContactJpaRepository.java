package br.com.hubregistro.bank.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface BankContactJpaRepository extends JpaRepository<BankContactEntity, UUID> {

    @Query("select c from BankContactEntity c where c.bankId = :bankId and c.deletedAt is null order by c.area, c.primary desc, c.name")
    List<BankContactEntity> findActive(@Param("bankId") UUID bankId);
}
