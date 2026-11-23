package com.spendlens.statement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findByOwnerIdOrderByUploadedAtDesc(Long ownerId);

    Optional<Statement> findByIdAndOwnerId(Long id, Long ownerId);
}
