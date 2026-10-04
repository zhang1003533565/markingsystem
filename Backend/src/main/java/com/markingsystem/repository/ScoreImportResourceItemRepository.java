package com.markingsystem.repository;

import com.markingsystem.entity.ScoreImportResourceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreImportResourceItemRepository extends JpaRepository<ScoreImportResourceItem, Long> {

    List<ScoreImportResourceItem> findByBatchIdOrderByIdAsc(String batchId);

    long countByBatchId(String batchId);
}
