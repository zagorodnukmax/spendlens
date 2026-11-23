package com.spendlens.statement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StatementTransactionRepository extends JpaRepository<StatementTransaction, Long> {

    List<StatementTransaction> findByStatementIdOrderByDateTimeDesc(Long statementId);

    @Modifying
    @Query("delete from StatementTransaction t where t.statement.id = :statementId")
    void deleteByStatementId(Long statementId);
}
