package com.markingsystem.repository;

import com.markingsystem.entity.ScoreImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScoreImportBatchRepository extends JpaRepository<ScoreImportBatch, String> {
}
