package com.markingsystem.repository;

import com.markingsystem.entity.ScoreImportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreImportItemRepository extends JpaRepository<ScoreImportItem, Long> {
    List<ScoreImportItem> findByBatchIdOrderByIdAsc(String batchId);

    List<ScoreImportItem> findByBatchIdAndStudentIdOrderByIdAsc(String batchId, Long studentId);
}
