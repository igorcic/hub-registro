package br.com.hubregistro.bank.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BankStatusChangeJpaRepository extends JpaRepository<BankStatusChangeEntity, UUID> {

    List<BankStatusChangeEntity> findByBankIdOrderByChangedAtDesc(UUID bankId);
}
