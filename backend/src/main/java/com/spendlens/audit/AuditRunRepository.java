package com.spendlens.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AuditRunRepository extends JpaRepository<AuditRun, Long> {

    Optional<AuditRun> findFirstByStatementIdOrderByIdDesc(Long statementId);

    @Modifying
    @Query("delete from AuditRun r where r.statement.id = :statementId")
    void deleteByStatementId(Long statementId);
}
