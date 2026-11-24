package com.spendlens.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditStepRepository extends JpaRepository<AuditStep, Long> {

    List<AuditStep> findByRunIdOrderByIdAsc(Long runId);

    @Modifying
    @Query("delete from AuditStep s where s.run.id in (select r.id from AuditRun r where r.statement.id = :statementId)")
    void deleteByStatementId(Long statementId);
}
